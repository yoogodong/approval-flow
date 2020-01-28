package dong.yoogo.approval.flow.constant;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ConditionIN {

    /**
     * 账户
     */
    private String accountNo;
    /**
     * 状态
     */
    private String flowStatus;
    /**
     * 业务类型
     */
    private List<String> functionCodeList;

    /**
     * 金额下限
     */
    private BigDecimal minAmount;
    /**
     * 金额上限
     */
    private BigDecimal maxAmount;

    /**
     * 制单渠道
     */
    private String channelOfCreated;


    /**
     * 复核日期起始
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate approvalDateFrom;
    /**
     * 复核日期终结
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate approvalDateTo;


    public FlowStatus getFlowStatus() {
        if (StringUtil.isEmpty(flowStatus))
            return null;
        return FlowStatus.valueOf(flowStatus);
    }

}
