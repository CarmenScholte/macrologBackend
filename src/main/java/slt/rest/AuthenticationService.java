package slt.rest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import slt.database.SettingsRepository;
import slt.database.UserAccountRepository;
import slt.database.entities.UserAccount;
import slt.dto.*;
import slt.security.ThreadLocalHolder;
import slt.security.UserInfo;
import slt.service.GoogleMailService;
import slt.util.JWTBuilder;
import slt.util.PasswordUtils;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
@Slf4j
public class AuthenticationService {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private GoogleMailService mailService;

    @Autowired
    private AccountService accountService;

    @PostMapping(path = "/authenticate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity authenticateUser(@RequestBody AuthenticationRequest request) {
        String username = request.getUsername();
        String hashedPassword = PasswordUtils.hashPassword(request.getPassword());
        log.info("Login attempt {} ", username);
        if (StringUtils.isEmpty(username)) username = request.getEmail(); // only 1 exists

        UserAccount userAccount = userAccountRepository.getUser(username);
        if (userAccount == null) {
            userAccount = userAccountRepository.getUserByEmail(username);
        }
        if (userAccount == null) {
            log.error("Not found");
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else if (!checkValidPassword(hashedPassword, userAccount)) {
            log.error("Unautorized");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else {
            JWTBuilder builder = new JWTBuilder();
            String jwt = builder.generateJWT(userAccount.getUsername(), userAccount.getId());

            MultiValueMap<String, String> responseHeaders = new HttpHeaders();
            responseHeaders.add("token", jwt);
            log.info("Login successful");
            UserAccountDto response = new UserAccountDto();
            response.setId(userAccount.getId());
            response.setUserName(userAccount.getUsername());
            response.setToken((jwt));
            response.setEmail(userAccount.getEmail());
            response.setAdmin(userAccount.isAdmin());
            return new ResponseEntity<>(response, responseHeaders, HttpStatus.ACCEPTED);
        }
    }

    @PostMapping(path = "/signup", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity signUp(@RequestBody RegistrationRequest request) {
        log.info(request.getEmail());
        String username = request.getUsername();
        String hashedPassword = PasswordUtils.hashPassword(request.getPassword());
        String email = request.getEmail();
        log.info("Add user attempt: {}", username);

        UserAccount account = userAccountRepository.getUser(username);
        if (account != null) {
            log.debug("Username of email already in use 1");
            return ResponseEntity.status(401).body("Username or email already in use");
        } else {
            UserAccount userByEmail = userAccountRepository.getUserByEmail(email);
            if (userByEmail != null) {
                log.debug("Username or email already in use 2");
                return ResponseEntity.status(401).body("Username or email already in use");
            } else {
                account = userAccountRepository.insertUser(username, hashedPassword, email);
            }
        }

        UserAccount newAccount = account;

        JWTBuilder builder = new JWTBuilder();
        String jwt = builder.generateJWT(username, newAccount.getId());

        MultiValueMap<String, String> responseHeaders = new HttpHeaders();
        responseHeaders.add("token", jwt);
        log.info("Signup successful");


        mailService.sendConfirmationMail(email, newAccount);

        UserAccountDto userDto = new UserAccountDto();
        userDto.setId(newAccount.getId());
        userDto.setUserName(newAccount.getUsername());
        userDto.setEmail(newAccount.getEmail());
        userDto.setToken(jwt);
        userDto.setAdmin(newAccount.isAdmin());
        return new ResponseEntity<>(userDto, responseHeaders, HttpStatus.ACCEPTED);
    }

    @PostMapping(path = "/resetPassword", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity resetPassword(@RequestBody ResetPasswordRequest request) {
        log.info("Reset email");
        String email = request.getEmail();
        UserAccount account = userAccountRepository.getUserByEmail(email);
        if (account != null) {
            String randomPassword = RandomStringUtils.randomAlphabetic(10);
            String hashedRandomPassword = PasswordUtils.hashPassword(randomPassword);
            account.setResetPassword(hashedRandomPassword);
            account.setResetDate(LocalDateTime.now());
            userAccountRepository.saveAccount(account);
            mailService.sendPasswordRetrievalMail(email, randomPassword, account);

            return ResponseEntity.ok("Email matches");
        } else {
            log.error("Account is null");
            return ResponseEntity.status(404).body("Email not found");
        }
    }

    @PostMapping(path = "/changePassword", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity changePassword(@RequestBody ChangePasswordRequest request) {
        String oldPasswordHashed = PasswordUtils.hashPassword(request.getOldPassword());
        String newPasswordHashed = PasswordUtils.hashPassword(request.getNewPassword());
        String confirmPasswordHashed = PasswordUtils.hashPassword(request.getConfirmPassword());
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();

        log.info("Update password attempt for userId: {}", userInfo.getUserId());

        UserAccount userAccount = userAccountRepository.getUserById(userInfo.getUserId());
        if (userAccount == null) {
            log.error("Not found");
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else if (!userAccount.getPassword().equals(oldPasswordHashed)) {
            log.error("Old password incorrect");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else {
            if (!newPasswordHashed.equals(confirmPasswordHashed)) {
                log.error("Passwords do not match");
                return new ResponseEntity<>("Passwords do not match", HttpStatus.BAD_REQUEST);
            } else {
                log.info("Passwords match");
                userAccount.setPassword(newPasswordHashed);
                userAccount.setResetDate(null);
                userAccount.setResetPassword(null);
                userAccountRepository.saveAccount(userAccount);
                return new ResponseEntity<>("OK", HttpStatus.OK);
            }
        }
    }

    @PostMapping(path = "/deleteAccount", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity deleteAccount(@RequestParam("password") String password) {
        String passwordHashed = PasswordUtils.hashPassword(password);
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        Integer userId = userInfo.getUserId();
        UserAccount userAccount = userAccountRepository.getUserById(userId);
        if (userAccount == null) {
            log.error("Account not found for userId: {}", userInfo.getUserId());
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else if (!userAccount.getPassword().equals(passwordHashed)) {
            log.error("Could not delete account: password incorrect");
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } else {
            accountService.deleteAccount(userId);
            return new ResponseEntity(HttpStatus.OK);
        }
    }

    private boolean checkValidPassword(String hashedPassword, UserAccount account) {
        boolean activePasswordOK = account.getPassword().equals(hashedPassword);
        if (!activePasswordOK) {
            boolean resettedPasswordOK = account.getResetPassword() != null &&
                    account.getResetPassword().equals(hashedPassword);

            boolean withinTimeFrame = account.getResetDate() != null &&
                    account.getResetDate().isAfter(LocalDateTime.now().minusMinutes(30));

            if (resettedPasswordOK && withinTimeFrame) {
                log.info("Password has been reset to verified new password");
                account.setPassword(hashedPassword);
                account.setResetDate(null);
                account.setResetDate(null);
                userAccountRepository.saveAccount(account);
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

}
