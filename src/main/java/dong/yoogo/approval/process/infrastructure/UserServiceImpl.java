package dong.yoogo.approval.process.infrastructure;

import dong.yoogo.approval.process.domain.adapter.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Override
    public String findEmailByUserId(Long userId) {
        return "Foo@Bar.com";
    }
}
