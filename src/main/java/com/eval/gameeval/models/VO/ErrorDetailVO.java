package com.eval.gameeval.models.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDetailVO {

    private String errorCode;
    private List<FieldErrorVO> errors;
}
