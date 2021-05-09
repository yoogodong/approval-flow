package dong.yoogo.approval.process.infrastructure;

import dong.yoogo.approval.process.domain.adapter.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MailServiceImpl implements MailService {
    @Override
    public void sendMail(String email) {
        log.info("发送邮件给{}",email);
    }
}
