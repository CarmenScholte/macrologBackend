package slt.rest;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import slt.service.GoogleMailService;
import slt.database.UserAccountRepository;
import slt.database.entities.UserAccount;
import slt.dto.SettingDto;
import slt.dto.UserAccountDto;
import slt.security.ThreadLocalHolder;
import slt.security.UserInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminService {

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private GoogleMailService mailService;

    @Autowired
    private GoogleMailService notificationMails;


    @GetMapping(path = "/getAllUsers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<UserAccountDto>> getAllUsers() {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        Integer userId = userInfo.getUserId();
        UserAccount userAccount = userAccountRepository.getUserById(userId);
        if (!userAccount.isAdmin()) {
            log.error("Not authorized to get all users");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else {
            List<UserAccount> userAccounts = userAccountRepository.getAllUsers();
            List<UserAccountDto> userAccountDtos = mapToDtos(userAccounts);
            return ResponseEntity.ok(userAccountDtos);
        }
    }

    @PostMapping(path = "/deleteAccount", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity deleteAccount(@RequestParam("userId") Integer deleteUserId) {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        Integer userId = userInfo.getUserId();
        UserAccount userAccount = userAccountRepository.getUserById(userId);
        UserAccount toBeDeletedAccount = userAccountRepository.getUserById(deleteUserId);
        if (!userAccount.isAdmin()) {
            log.error("Not authorized to delete account");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else if (toBeDeletedAccount == null) {
            log.error("Account not found for userId: {}", userInfo.getUserId());
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else if (toBeDeletedAccount.isAdmin()){
            log.error("Cannot delete admin account");
            return new ResponseEntity((HttpStatus.BAD_REQUEST));
        } else {
            accountService.deleteAccount(deleteUserId);
            return new ResponseEntity(HttpStatus.OK);
        }
    }

    @GetMapping(path = "/mail", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map> getMailConfig() {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        Integer userId = userInfo.getUserId();
        UserAccount userAccount = userAccountRepository.getUserById(userId);
        if (!userAccount.isAdmin()) {
            log.error("Not authorized to get mail config");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else {
            return ResponseEntity.ok(mailService.getMailStatus());
        }
    }

    @PostMapping(path = "/mail", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity storeMailSetting(@RequestBody SettingDto code) throws IOException {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        Integer userId = userInfo.getUserId();
        UserAccount userAccount = userAccountRepository.getUserById(userId);
        if (!userAccount.isAdmin()) {
            log.error("Not authorized to store mail settings");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else {
            log.debug("Storing setting " + code.getName() + " - " + code.getValue());
            mailService.registerWithCode(code.getValue());
            return new ResponseEntity(HttpStatus.OK);
        }
    }
    @PostMapping(path = "/mail/testmail", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity sendTestMail(@RequestBody SettingDto code) throws IOException {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        Integer userId = userInfo.getUserId();
        UserAccount userAccount = userAccountRepository.getUserById(userId);
        if (!userAccount.isAdmin()) {
            log.error("Not authorized to send testmail");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else {
            log.debug("Sending mail to  " + code.getName() + " - " + code.getValue());
            mailService.sendTestMail(code.getValue());
            return new ResponseEntity(HttpStatus.OK);
        }
    }

    private List<UserAccountDto> mapToDtos(List<UserAccount> accounts) {
        List<UserAccountDto> dtos = new ArrayList<>();
        for (UserAccount account : accounts) {
            dtos.add(mapToDto(account));
        }
        return dtos;
    }

    private UserAccountDto mapToDto(UserAccount account) {
        return new UserAccountDto(
                account.getId(),
                null,
                account.getUsername(),
                account.getEmail(),
                account.isAdmin());
    }
}
