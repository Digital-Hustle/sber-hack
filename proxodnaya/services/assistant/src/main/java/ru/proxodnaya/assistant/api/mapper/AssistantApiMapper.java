package ru.proxodnaya.assistant.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ru.proxodnaya.assistant.api.dto.response.AskRs;
import ru.proxodnaya.assistant.api.dto.response.SourceRs;
import ru.proxodnaya.assistant.api.dto.response.StatusRs;
import ru.proxodnaya.assistant.api.dto.response.TableRs;
import ru.proxodnaya.assistant.api.dto.response.ToolDataRs;
import ru.proxodnaya.assistant.features.assistant.model.Answer;
import ru.proxodnaya.assistant.features.assistant.model.LlmStatus;
import ru.proxodnaya.assistant.features.assistant.model.Validation;

/**
 * Модель помощника → DTO.
 */
@Component
public class AssistantApiMapper {

    /**
     * Ответ.
     *
     * @param answer ответ
     * @return DTO; сырые данные — только при отказе
     */
    public AskRs convert(Answer answer) {
        List<SourceRs> sources = answer.sources().stream()
                .map(source -> new SourceRs(source.tool(), source.args(), source.at(), source.ok())).toList();
        List<ToolDataRs> data = answer.validation() == Validation.REFUSED
                ? answer.sources().stream().map(source -> new ToolDataRs(source.tool(), source.result())).toList()
                : null;
        TableRs table = answer.table() == null ? null
                : new TableRs(answer.table().tool(), answer.table().columns(), answer.table().rows());
        return new AskRs(answer.text(), answer.validation().name(), sources, table, data);
    }

    /**
     * Состояние.
     *
     * @param status состояние
     * @return DTO
     */
    public StatusRs convert(LlmStatus status) {
        return new StatusRs(status.provider(), status.model(), status.lastCallAt(), status.lastOk(), status.lastError(),
                status.callsLastHour(), status.avgLatencyMs(), status.refusedLastHour());
    }
}
