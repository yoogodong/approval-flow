package dong.yoogo.approval.flow.domain;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.Digits;
import java.math.BigDecimal;


/**
 * 代表复核员
 */
@Embeddable
@Getter
@NoArgsConstructor
@Data
public class Approver {


    /**
     * 复核员 id
     */
    @Column(name = "APPROVER_NO")
    private String userNo;

    /**
     * 最小可复核金额
     */
    @Digits(integer = 19, fraction = 2)
    private BigDecimal minAmount;
    /**
     * 最大可复核金额
     */
    @Digits(integer = 19, fraction = 2)
    private BigDecimal maxAmount;
    /**
     * 最大对外支付金额
     */
    @Digits(integer = 19, fraction = 2)
    @Column(name = "MAX_DEAL_AMOUNT")
    private BigDecimal dealAmount;




    Approver(String userNo, BigDecimal minAmount, BigDecimal maxAmount, BigDecimal dealAmount) {
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.dealAmount = dealAmount;
        this.userNo = userNo;
    }

    /**
     *  是否在最大对外支付金额之内
     */
    boolean couldDeal(BigDecimal transactionAmount) {
        return null != dealAmount && null != transactionAmount && dealAmount.compareTo(transactionAmount) >= 0;
    }


    /**
     * 是否可见
     */
    public boolean isVisible(BigDecimal transactionAmount) {
        return (null == transactionAmount) 
        		|| transactionAmount.compareTo(minAmount) >= 0 && transactionAmount.compareTo(maxAmount) <= 0;
    }
}
