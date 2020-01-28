package dong.yoogo.approval.flow.domain;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.math.BigDecimal;

/**
 * 落地复核相关
 */
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Embeddable
public class LandingAppoval {

    /**
     * 机构号
     */
    @Column(name = "DEPT_SEQ", length = 20)
    private final Long departmentNo;

    /**
     * 落地复核限额
     */
    @Column(name = "LANDING_LIMIT", precision = 21, scale = 2)
    private final BigDecimal landingLimit;

    /**
     * 落地复核,柜员id
     */
    @Column(name = "TELLER_ID", length = 20)
    private String tellerId;
    /**
     * 落地复核,柜员name
     */
    @Column(name = "TELLER_NAME", length = 20)
    private String tellerName;
    /**
     * 落地复核, 驳回原因
     */
    @Column(name = "REJECT_REASON", length = 250)
    private String rejectReason;


    /**
     * 是否需要落地复核
     */
    boolean needLandingApproval(BigDecimal transactionAmount) {
        return (landingLimit != null && transactionAmount.compareTo(landingLimit) >= 0);
    }

    /**
     * 落地复核同意
     */
    public void approve(String tellerId, String tellerName) {
        this.tellerId = tellerId;
        this.tellerName = tellerName;
    }

    /**
     * 落地复核驳回
     */
    public void reject(String tellerId, String tellerName, String rejectReason) {
        this.tellerId = tellerId;
        this.tellerName = tellerName;
        this.rejectReason = rejectReason;
    }

}
