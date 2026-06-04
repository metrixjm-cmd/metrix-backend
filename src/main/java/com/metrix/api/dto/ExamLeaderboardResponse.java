package com.metrix.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Respuesta del ranking de exámenes por rol. */
@Data
@Builder
public class ExamLeaderboardResponse {

    private List<ExamRankEntry> ranking;

    /** Posición del usuario autenticado en el ranking (null si no ha tomado exámenes). */
    private ExamRankEntry myEntry;

    private int totalParticipants;

    @Data
    @Builder
    public static class ExamRankEntry {
        private int    rank;
        private String userId;
        private String userName;
        private String userNumero;
        private String role;        // rol principal del usuario
        private String storeId;
        private String storeName;
        private double avgScore;
        private int    examsTaken;
        private int    examsPassed;
        private int    passRate;    // 0–100
    }
}
