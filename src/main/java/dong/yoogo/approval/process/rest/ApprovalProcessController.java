package dong.yoogo.approval.process.web.rest;

import dong.yoogo.approval.process.rest.dto.ApproveIN;
import dong.yoogo.approval.process.rest.dto.CreateProcessIN;
import dong.yoogo.approval.process.rest.dto.RejectIN;
import dong.yoogo.approval.process.service.ApprovalProcessService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;


@RestController
@NoArgsConstructor
@AllArgsConstructor
@RequestMapping("/approval-process")
@Slf4j
public class ApprovalProcessController {

    private ApprovalProcessService service;

    /**
     * 创建 flow
     */
    @PostMapping("/create")
    public void createProcess(@RequestBody @Valid CreateProcessIN in) {
        service.createApprovalProcess(in.toEntity());
    }


    /**
     * 同意
     */
    @PostMapping("/approve")
    public void approve(@RequestBody @Valid ApproveIN in) {
        log.info("\n 单笔同意,参数={}\n", in);
        service.approve(in.getTransactionNo(), in.getApproverId());
    }

    /**
     * 驳回
     * @param in
     */
    public void reject(@RequestBody @Valid RejectIN in){
        service.reject(in.getTransactionNo(),in.getApproverId(),in.getRejectReason());
    }

}

