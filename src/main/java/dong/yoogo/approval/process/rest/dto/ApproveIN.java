package dong.yoogo.approval.process.rest.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApproveIN {
    @NotEmpty String approverId;
    @NotEmpty String transactionNo;
}
