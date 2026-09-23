package com.example.SWP391_G2_SE2055_JV.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex,
                                                            HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI(), null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid credentials", request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        BindingResult result = ex.getBindingResult();
        List<ApiError.FieldError> fieldErrors = result.getFieldErrors().stream()
            .map(fe -> ApiError.FieldError.builder()
                .field(fe.getField())
                .message(fe.getDefaultMessage())
                .build())
            .toList();
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed",
            request.getRequestURI(), fieldErrors);
    }

    /**
     * Tham số trên URL sai kiểu — ví dụ {@code GET /rooms?status=FOO} hoặc id không phải UUID.
     *
     * <p>Đây là lỗi của client nên phải là 400. Thiếu handler này, Spring không kịp trả 400
     * mặc định vì {@link #handleGeneral} (bắt {@code Exception}) chặn trước, và client nhận
     * 500 "An unexpected error occurred" — không biết mình gửi sai ở đâu.
     *
     * <p>Không lặp lại giá trị client gửi vào câu thông báo, chỉ nêu tên tham số.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
            "Giá trị của tham số '" + ex.getName() + "' không hợp lệ.",
            request.getRequestURI(), null);
    }

    /**
     * Thiếu tham số bắt buộc trên URL — ví dụ gọi {@code /housekeeping/tasks/assignable-staff}
     * mà không kèm {@code date}.
     *
     * <p>Cùng họ với {@link #handleTypeMismatch}: lỗi của client nên phải là 400. Thiếu handler
     * này thì {@link #handleGeneral} nuốt mất và client nhận 500 "An unexpected error occurred",
     * không biết mình quên tham số nào.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
            "Thiếu tham số bắt buộc '" + ex.getParameterName() + "'.",
            request.getRequestURI(), null);
    }

    /**
     * Body không đọc được: JSON hỏng, hoặc một trường có giá trị không map được — ví dụ
     * {@code {"targetStatus": "FOO"}} trong khi trường là enum. Cùng lý do với
     * {@link #handleTypeMismatch}: lỗi của client, không được rơi xuống 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
            "Dữ liệu gửi lên không đúng định dạng hoặc có giá trị không hợp lệ.",
            request.getRequestURI(), null);
    }

    /**
     * Vi phạm ràng buộc DB (UNIQUE, khóa ngoại, CHECK). Service vẫn phải tự kiểm tra trước
     * để trả thông báo rõ ràng; đây là lưới an toàn cho race condition (2 request cùng tạo
     * trùng email) và các chỗ chưa kiểm tra, để không rơi xuống 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT,
            "Dữ liệu bị trùng hoặc đang được tham chiếu bởi dữ liệu khác",
            request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
            request.getRequestURI(), null);
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message,
                                                    String path, List<ApiError.FieldError> fieldErrors) {
        ApiError error = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .path(path)
            .fieldErrors(fieldErrors)
            .build();
        return ResponseEntity.status(status).body(error);
    }
}