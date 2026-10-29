package com.app.winterarc.ui.auth;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * "Continue with Google" via Credential Manager: shows the system account sheet and hands the
 * resulting Google ID token to {@link AuthViewModel}, which exchanges it with Supabase Auth.
 */
final class GoogleSignIn {
    private GoogleSignIn() {}

    static void start(Activity activity, AuthViewModel viewModel) {
        String clientId = viewModel.googleServerClientId();
        if (clientId == null) {
            viewModel.googleFailed(AuthError.NOT_CONFIGURED);
            return;
        }
        if (AuthRepository.TEST_GOOGLE_CLIENT.equals(clientId)) {
            viewModel.googleToken(null); // debug test provider: no system sheet
            return;
        }
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(clientId).build())
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            viewModel.googleToken(GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
                        } else {
                            viewModel.googleFailed(AuthError.UNKNOWN);
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) viewModel.googleFailed(AuthError.GOOGLE_CANCELLED);
                        else if (e instanceof NoCredentialException) viewModel.googleFailed(AuthError.GOOGLE_NO_ACCOUNT);
                        else viewModel.googleFailed(AuthError.UNKNOWN);
                    }
                });
    }
}
