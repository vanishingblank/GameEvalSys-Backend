package com.eval.gameeval.exception;

import com.eval.gameeval.models.VO.ErrorDetailVO;
import com.eval.gameeval.models.VO.FieldErrorVO;
import com.eval.gameeval.models.VO.ResponseVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CODE_VALIDATION = "VALIDATION_ERROR";
    private static final String CODE_JSON_PARSE = "JSON_PARSE_ERROR";
    private static final String CODE_TYPE_MISMATCH = "TYPE_MISMATCH";
    private static final String CODE_MISSING_PARAM = "MISSING_PARAM";
    private static final String CODE_MISSING_HEADER = "MISSING_HEADER";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseVO<ErrorDetailVO>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex
    ) {
        List<FieldErrorVO> errors = toFieldErrors(ex.getBindingResult().getFieldErrors());
        String message = firstMessage(errors, "参数错误");
        return badRequest(message, CODE_VALIDATION, errors);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ResponseVO<ErrorDetailVO>> handleBindException(BindException ex) {
        List<FieldErrorVO> errors = toFieldErrors(ex.getBindingResult().getFieldErrors());
        String message = firstMessage(errors, "参数错误");
        return badRequest(message, CODE_VALIDATION, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseVO<ErrorDetailVO>> handleConstraintViolation(
            ConstraintViolationException ex
    ) {
        List<FieldErrorVO> errors = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());
        String message = firstMessage(errors, "参数错误");
        return badRequest(message, CODE_VALIDATION, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseVO<ErrorDetailVO>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex
    ) {
        return badRequest("请求体格式错误", CODE_JSON_PARSE, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseVO<ErrorDetailVO>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        List<FieldErrorVO> errors = List.of(
                new FieldErrorVO(ex.getName(), "参数类型错误")
        );
        return badRequest("参数类型错误", CODE_TYPE_MISMATCH, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseVO<ErrorDetailVO>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex
    ) {
        List<FieldErrorVO> errors = List.of(
                new FieldErrorVO(ex.getParameterName(), "缺少请求参数")
        );
        return badRequest("缺少请求参数", CODE_MISSING_PARAM, errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ResponseVO<ErrorDetailVO>> handleMissingRequestHeader(
            MissingRequestHeaderException ex
    ) {
        List<FieldErrorVO> errors = List.of(
                new FieldErrorVO(ex.getHeaderName(), "缺少请求头")
        );
        return badRequest("缺少请求头", CODE_MISSING_HEADER, errors);
    }

    private ResponseEntity<ResponseVO<ErrorDetailVO>> badRequest(
            String message,
            String errorCode,
            List<FieldErrorVO> errors
    ) {
        ErrorDetailVO detail = new ErrorDetailVO(errorCode, errors);
        return ResponseEntity.badRequest().body(new ResponseVO<>(400, message, detail));
    }

    private List<FieldErrorVO> toFieldErrors(List<FieldError> fieldErrors) {
        List<FieldErrorVO> errors = new ArrayList<>();
        for (FieldError fieldError : fieldErrors) {
            String message = Optional.ofNullable(fieldError.getDefaultMessage())
                    .filter(m -> !m.isBlank())
                    .orElse("参数错误");
            errors.add(new FieldErrorVO(fieldError.getField(), message));
        }
        return errors;
    }

    private FieldErrorVO toFieldError(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() == null
                ? ""
                : violation.getPropertyPath().toString();
        String message = Optional.ofNullable(violation.getMessage())
                .filter(m -> !m.isBlank())
                .orElse("参数错误");
        return new FieldErrorVO(field, message);
    }

    private String firstMessage(List<FieldErrorVO> errors, String fallback) {
        return errors.stream()
                .map(FieldErrorVO::getMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(fallback);
    }
}
