package com.metrix.api.service;

import com.metrix.api.model.Execution;
import com.metrix.api.model.ProcessStep;
import com.metrix.api.model.RecurringDay;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cierra los días pasados de una tarea repetitiva y deja el de hoy en Pendiente.
 * Un día que no se hizo queda en el historial y no pinta los pasos de hoy.
 */
@Component
public class RecurringCycleRoller {

    private static final int WINDOW_DAYS = 45;
    private static final int MAX_HISTORY = 60;

    private final ZoneId zone;

    public RecurringCycleRoller(
            @Value("${metrix.operational.zone:America/Mexico_City}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    public String todayIso(Instant now) {
        return LocalDate.ofInstant(now, zone).toString();
    }

    /** @return true si el documento cambió y hay que guardarlo */
    public boolean roll(Task task, Instant now) {
        if (task == null || !task.isRecurring()) {
            return false;
        }
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate cycle = cycleOf(task, today);
        if (!cycle.isBefore(today)) {
            if (task.getCycleDate() == null) {
                task.setCycleDate(today.toString());
                return true;
            }
            return false;
        }

        LocalDate windowStart = today.minusDays(WINDOW_DAYS);
        LocalDate cursor = cycle.isBefore(windowStart) ? windowStart : cycle;
        while (cursor.isBefore(today)) {
            if (isScheduled(task, cursor)) {
                String status = cursor.equals(cycle) ? outcome(task) : RecurringDay.MISSED;
                Boolean onTime = cursor.equals(cycle) && task.getExecution() != null
                        ? task.getExecution().getOnTime()
                        : null;
                addHistory(task, cursor, status, onTime);
            }
            cursor = cursor.plusDays(1);
        }
        resetToday(task);
        task.setCycleDate(today.toString());
        return true;
    }

    private LocalDate cycleOf(Task task, LocalDate today) {
        if (task.getCycleDate() != null && !task.getCycleDate().isBlank()) {
            return LocalDate.parse(task.getCycleDate());
        }
        if (task.getCreatedAt() != null) {
            return LocalDate.ofInstant(task.getCreatedAt(), zone);
        }
        return today;
    }

    private boolean isScheduled(Task task, LocalDate date) {
        List<String> days = task.getRecurrenceDays();
        if (days == null || days.isEmpty()) {
            return false;
        }
        String code = weekdayCode(date.getDayOfWeek());
        return days.stream().anyMatch(day -> code.equals(normalize(day)));
    }

    private String outcome(Task task) {
        Execution execution = task.getExecution();
        if (execution == null || execution.getStatus() == null) {
            return RecurringDay.MISSED;
        }
        return switch (execution.getStatus()) {
            case COMPLETED -> RecurringDay.COMPLETED;
            case FAILED -> RecurringDay.FAILED;
            case PENDING, IN_PROGRESS -> RecurringDay.MISSED;
        };
    }

    private void addHistory(Task task, LocalDate date, String status, Boolean onTime) {
        if (task.getDayHistory() == null) {
            task.setDayHistory(new ArrayList<>());
        }
        String iso = date.toString();
        boolean exists = task.getDayHistory().stream().anyMatch(day -> iso.equals(day.getDate()));
        if (exists) {
            return;
        }
        task.getDayHistory().add(RecurringDay.builder()
                .date(iso)
                .status(status)
                .onTime(onTime)
                .build());
        if (task.getDayHistory().size() > MAX_HISTORY) {
            task.setDayHistory(new ArrayList<>(
                    task.getDayHistory().subList(task.getDayHistory().size() - MAX_HISTORY, task.getDayHistory().size())));
        }
    }

    private void resetToday(Task task) {
        if (task.getProcesses() != null) {
            for (ProcessStep step : task.getProcesses()) {
                step.setCompleted(false);
                step.setCompletedAt(null);
                step.setNotes(null);
            }
        }
        Execution execution = task.getExecution() != null ? task.getExecution() : Execution.builder().build();
        execution.setStatus(TaskStatus.PENDING);
        execution.setStartedAt(null);
        execution.setFinishedAt(null);
        execution.setOnTime(null);
        if (execution.getEvidence() != null) {
            execution.getEvidence().setImages(new ArrayList<>());
            execution.getEvidence().setVideos(new ArrayList<>());
        }
        task.setExecution(execution);
        task.setComments(null);
    }

    static String weekdayCode(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "LUN";
            case TUESDAY -> "MAR";
            case WEDNESDAY -> "MIE";
            case THURSDAY -> "JUE";
            case FRIDAY -> "VIE";
            case SATURDAY -> "SAB";
            case SUNDAY -> "DOM";
        };
    }

    private static String normalize(String day) {
        return day == null ? "" : day.trim().toUpperCase(Locale.ROOT);
    }
}
