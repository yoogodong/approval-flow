package dong.yoogo.approval.process.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class RejectIN extends ApproveIN {
    private String rejectReason;
}
