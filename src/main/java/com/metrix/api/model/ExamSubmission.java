package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.metrix.api.dto.ExamAnswer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de la respuesta de un colaborador a un examen (Sprint 19).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exam_submissions")
@CompoundIndexes({
        @CompoundIndex(name = "idx_sub_exam",  def = "{'exam_id': 1, 'submitted_at': -1}"),
        @CompoundIndex(name = "idx_sub_user",  def = "{'user_id': 1, 'submitted_at': -1}"),
        @CompoundIndex(name = "idx_sub_store", def = "{'store_id': 1, 'submitted_at': -1}")
})
public class ExamSubmission {

    @Id
    private String id;

    @Field("exam_id")
    private String examId;

    /** Título desnormalizado para mantener histórico legible. */
    @Field("exam_title")
    private String examTitle;

    @Field("user_id")
    private String userId;

    @Field("user_name")
    private String userName;

    @Field("user_numero")
    private String userNumero;

    @Field("store_id")
    private String storeId;

    /** Legacy: índices para MULTIPLE_CHOICE/TRUE_FALSE (pre-E2). Null en nuevas submissions. */
    @Field("answers")
    private List<Integer> answers;

    /** Respuestas detalladas por pregunta — soporta todos los tipos. */
    @Builder.Default
    @Field("detailed_answers")
    private List<ExamAnswer> detailedAnswers = new ArrayList<>();

    /** Puntaje obtenido (0.0–100.0). */
    @Field("score")
    private double score;

    @Field("passed")
    private boolean passed;

    @Field("time_taken_seconds")
    private Integer timeTakenSeconds;

    @Field("submitted_at")
    private Instant submittedAt;

    /** Desglose pregunta por pregunta persistido al momento del submit. */
    @Builder.Default
    @Field("question_results")
    private List<SubmissionQuestionResult> questionResults = new ArrayList<>();

    /** true una vez que ADMIN/GERENTE revisa las respuestas OPEN_TEXT pendientes. */
    @Builder.Default
    @Field("reviewed")
    private boolean reviewed = false;

    /** Flags de actividad sospechosa detectados en submit(). */
    @Builder.Default
    @Field("fraud_flags")
    private List<String> fraudFlags = new ArrayList<>();

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;
}
