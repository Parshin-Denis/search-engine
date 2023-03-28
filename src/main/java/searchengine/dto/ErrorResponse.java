package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class ErrorResponse {
    private boolean result = false;
    private String error;

    public ErrorResponse(String error) {
        this.error = error;
    }
}
