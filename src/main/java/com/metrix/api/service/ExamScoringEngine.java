package com.metrix.api.service;

import com.metrix.api.dto.ExamAnswer;
import com.metrix.api.model.ExamQuestion;
import com.metrix.api.model.QuestionType;
import com.metrix.api.model.SubmissionQuestionResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Motor de evaluación automática de exámenes para METRIX (E2).
 * <p>
 * Soporta cuatro tipos de pregunta:
 * <ul>
 *   <li>MULTIPLE_CHOICE / TRUE_FALSE → todo o nada</li>
 *   <li>MULTI_SELECT → scoring parcial proporcional con penalización</li>
 *   <li>OPEN_TEXT → keyword matching + flag para revisión manual</li>
 * </ul>
 */
@Component
public class ExamScoringEngine {

    public record ScoringResult(
            double earnedPoints,
            double totalPoints,
            int score,              // 0–100
            boolean hasPendingReview,
            List<SubmissionQuestionResult> questionResults
    ) {}

    public ScoringResult evaluate(List<ExamQuestion> questions, List<ExamAnswer> answers) {
        double totalPoints  = 0;
        double earnedPoints = 0;
        boolean hasPendingReview = false;
        List<SubmissionQuestionResult> results = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            ExamQuestion q = questions.get(i);
            ExamAnswer   a = (i < answers.size()) ? answers.get(i) : new ExamAnswer();
            totalPoints += q.getPoints();

            SubmissionQuestionResult result = switch (q.getType()) {
                case MULTIPLE_CHOICE, TRUE_FALSE -> scoreSingleChoice(q, a);
                case MULTI_SELECT                -> scoreMultiSelect(q, a);
                case OPEN_TEXT                   -> scoreOpenText(q, a);
            };

            earnedPoints += result.getPointsEarned();
            if (result.isPendingReview()) hasPendingReview = true;
            results.add(result);
        }

        int score = totalPoints > 0
                ? (int) Math.round((earnedPoints / totalPoints) * 100.0)
                : 0;

        return new ScoringResult(earnedPoints, totalPoints, score, hasPendingReview, results);
    }

    // ── MULTIPLE_CHOICE / TRUE_FALSE ──────────────────────────────────────

    private SubmissionQuestionResult scoreSingleChoice(ExamQuestion q, ExamAnswer a) {
        int selected = a.getSelectedIndex() != null ? a.getSelectedIndex() : -1;
        boolean correct = selected == q.getCorrectOptionIndex();
        double earned   = correct ? q.getPoints() : 0;

        return SubmissionQuestionResult.builder()
                .questionText(q.getQuestionText())
                .type(q.getType())
                .options(q.getOptions())
                .selectedIndex(selected)
                .correctIndex(q.getCorrectOptionIndex())
                .correct(correct)
                .pointsEarned(earned)
                .pointsMax(q.getPoints())
                .explanation(q.getExplanation())
                .build();
    }

    // ── MULTI_SELECT (scoring parcial proporcional con penalización) ───────

    /**
     * Fórmula: ratio = (correctHits - wrongHits) / totalCorrect
     * <p>
     * Ejemplo: 3 correctas disponibles, usuario marca 2 correctas + 1 incorrecta.
     * ratio = (2 - 1) / 3 = 0.33 → earned = 0.33 × points
     * <p>
     * Nunca negativo (clamped a 0).
     */
    private SubmissionQuestionResult scoreMultiSelect(ExamQuestion q, ExamAnswer a) {
        List<Integer> selected = a.getSelectedIndexes() != null ? a.getSelectedIndexes() : List.of();
        List<Integer> correct  = q.getCorrectOptionIndexes() != null ? q.getCorrectOptionIndexes() : List.of();

        Set<Integer> correctSet  = new HashSet<>(correct);
        Set<Integer> selectedSet = new HashSet<>(selected);

        long correctHits = selectedSet.stream().filter(correctSet::contains).count();
        long wrongHits   = selectedSet.stream().filter(i -> !correctSet.contains(i)).count();
        int  totalCorrect = correctSet.size();

        double ratio  = totalCorrect > 0
                ? Math.max(0.0, (double)(correctHits - wrongHits) / totalCorrect)
                : 0.0;
        double earned = ratio * q.getPoints();
        boolean fullyCorrect = ratio == 1.0;

        return SubmissionQuestionResult.builder()
                .questionText(q.getQuestionText())
                .type(q.getType())
                .options(q.getOptions())
                .selectedIndexes(selected)
                .correctIndexes(correct)
                .correct(fullyCorrect)
                .pointsEarned(Math.round(earned * 100.0) / 100.0)
                .pointsMax(q.getPoints())
                .explanation(q.getExplanation())
                .build();
    }

    // ── OPEN_TEXT (keyword matching + flag manual) ────────────────────────

    /**
     * Si matchRatio >= 0.7 → auto-aprobado con puntaje proporcional.
     * Si matchRatio < 0.7  → puntaje 0, flagged para revisión manual.
     */
    private SubmissionQuestionResult scoreOpenText(ExamQuestion q, ExamAnswer a) {
        String text     = a.getTextAnswer() != null ? a.getTextAnswer().toLowerCase() : "";
        List<String> kw = q.getAcceptedKeywords() != null ? q.getAcceptedKeywords() : List.of();

        long matched = kw.stream()
                .filter(k -> text.contains(k.toLowerCase()))
                .count();

        double matchRatio    = kw.isEmpty() ? 0.0 : (double) matched / kw.size();
        boolean autoApproved = matchRatio >= 0.7;
        boolean pendingReview = !autoApproved && !text.isBlank();

        double earned = autoApproved
                ? Math.round(matchRatio * q.getPoints() * 100.0) / 100.0
                : 0.0;

        return SubmissionQuestionResult.builder()
                .questionText(q.getQuestionText())
                .type(q.getType())
                .textAnswer(a.getTextAnswer())
                .acceptedKeywords(kw)
                .correct(autoApproved)
                .pendingReview(pendingReview)
                .pointsEarned(earned)
                .pointsMax(q.getPoints())
                .explanation(q.getExplanation())
                .build();
    }
}
