package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class UserVO implements Serializable {
    private Long id;
    private String username;
    private String name;
    private String role;
    private Boolean isEnabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
