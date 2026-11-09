package com.divinedoll.launcher.authenticator;

import androidx.annotation.NonNull;

import com.divinedoll.launcher.authenticator.listener.LoginListener;
import com.divinedoll.launcher.authenticator.accounts.Account;

public interface BackgroundLogin {
    void createAccount(@NonNull LoginListener loginListener, String code);
    void refreshAccount(@NonNull LoginListener loginListener, Account account);
    interface Creator {
        BackgroundLogin create();
    }
}
