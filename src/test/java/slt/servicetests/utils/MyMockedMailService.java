package slt.servicetests.utils;

import slt.config.GoogleConfig;
import slt.connectivity.google.GoogleClient;
import slt.database.SettingsRepository;
import slt.database.entities.UserAccount;
import slt.service.GoogleMailService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MyMockedMailService extends GoogleMailService {

    private HashMap<String,String> resetPasswords = new HashMap<>();
    private List<String> confirmationMailsSend = new ArrayList<>();

    public MyMockedMailService(SettingsRepository settingsRepository,
                               GoogleConfig googleConfig,
                               GoogleClient googleClient){
        super(settingsRepository,googleConfig,googleClient);
    }

    @Override
    public void sendPasswordRetrievalMail(String email, String unhashedTemporaryPassword, UserAccount account) {
        resetPasswords.put(email, unhashedTemporaryPassword);
    }

    public String getResettedPassword(String email) {
        return resetPasswords.get(email);
    }

    @Override
    public void sendConfirmationMail(String email, UserAccount account) {
        confirmationMailsSend.add(email);
    }

    public boolean verifyConfirmationSendTo(String email){
        return confirmationMailsSend.remove(email);
    }
}
