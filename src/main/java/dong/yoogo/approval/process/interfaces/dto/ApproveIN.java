package dong.yoogo.approval.process.interfaces.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApproveIN {
    @NonNull private String approverId;
    @NonNull private String processId;
}
