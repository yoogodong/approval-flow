package dong.yoogo.approval.flow.domain;

import dong.yoogo.approval.flow.constant.ApproverResult;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.time.LocalDateTime;

@Embeddable
@Data
@RequiredArgsConstructor
@NoArgsConstructor
public class HandledApprover {
    @NonNull
    @Column(name = "APPROVER_NO")
    private String userNo;
    @NonNull
    @Column(name = "APPROVE_TIME")
    private LocalDateTime handleTime;
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private ApproverResult result;
    @Column(name = "REJECT_REASON")
    private String rejectReason;
    
    //以下为非业务字段
    //渠道
    @Column(name = "CHANNEL_ID")
    private String channelId;

    //客户端IP
    @Column(name = "CLIENT_IP")
    private String clientIp;

    //服务端名称
    @Column(name = "TRAN_SERVICE_NAME")
    private String tranServiceName;

    //服务端IP
    @Column(name = "TRAN_SERVICE_IP")
    private String tranServiceIp;


    
    
}
