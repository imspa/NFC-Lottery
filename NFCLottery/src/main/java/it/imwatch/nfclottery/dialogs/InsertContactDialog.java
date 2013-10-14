/*
 * Copyright 2013 i'm SpA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.imwatch.nfclottery.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import de.keyboardsurfer.android.widget.crouton.Style;
import it.imwatch.nfclottery.DataHelper;
import it.imwatch.nfclottery.MainActivity;
import it.imwatch.nfclottery.R;

import java.util.ArrayList;
import java.util.regex.Pattern;

import static it.imwatch.nfclottery.Const.DEBUG;

/**
 * A dialog that allows the user to manually add a contact to the database.
 */
public class InsertContactDialog extends DialogFragment {

    private static final String TAG = InsertContactDialog.class.getSimpleName();
    private static final Pattern NAME_PATTERN =
        Pattern.compile("^\\p{L}+\\s+(?:\\p{L}+\\s*)+", Pattern.CASE_INSENSITIVE);

    private static final String EXTRA_NAME_ERROR = "name_error", EXTRA_EMAIL_ERROR = "email_error";
    private float mErrorAnimTranslateY;

    private TextView mEmailErrorTextView, mNameErrorTextView;
    private int mNameErrorState = 1, mEmailErrorState = 1;

    private View.OnFocusChangeListener mFocusWatcher = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus) {
                final CharSequence text = ((TextView) v).getText();
                final Context context = v.getContext();

                if (v.getId() == R.id.txt_edit_email) {
                    validateEmailInput(text, context);
                }
                else if (v.getId() == R.id.txt_edit_name) {
                    validateNameInput(text);
                }
            }
        }
    };

    private EditText mEmailEditText, mNameEditText, mOrganizationEditText, mTitleEditText;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        final Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Not attached to Activity: cannot build dialog");
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // Get the layout inflater
        LayoutInflater inflater = LayoutInflater.from(activity);
        final View rootView = inflater.inflate(R.layout.dialog_insert, null);
        if (rootView == null) {
            Log.e(TAG, "Cannot inflate the dialog layout!");
            return null;
        }

        mErrorAnimTranslateY = getResources().getDimensionPixelSize(R.dimen.error_anim_translate_y);

        mEmailErrorTextView = (TextView) rootView.findViewById(R.id.lbl_email_error);
        mNameErrorTextView = (TextView) rootView.findViewById(R.id.lbl_name_error);

        // Restore instance state (if any)
        if (savedInstanceState != null) {
            mNameErrorState = savedInstanceState.getInt(EXTRA_NAME_ERROR, 0);
            mEmailErrorState = savedInstanceState.getInt(EXTRA_EMAIL_ERROR, 0);

            if (mNameErrorState == 1) showNameError();
            if (mEmailErrorState == 1) {
                showEmailError(activity.getString(R.string.error_emailinput_invalid), 1);
            }
            else if (mEmailErrorState == 2) {
                showEmailError(activity.getString(R.string.error_emailinput_duplicate), 2);
            }
        }

        mEmailEditText = (EditText) rootView.findViewById(R.id.txt_edit_email);
        mNameEditText = (EditText) rootView.findViewById(R.id.txt_edit_name);
        mOrganizationEditText = (EditText) rootView.findViewById(R.id.txt_edit_organization);
        mTitleEditText = (EditText) rootView.findViewById(R.id.txt_edit_title);

        // Add the check for a valid email address and names
        mEmailEditText.setOnFocusChangeListener(mFocusWatcher);
        mNameEditText.setOnFocusChangeListener(mFocusWatcher);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(rootView)
               .setPositiveButton(android.R.string.ok, null)
               .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       final Dialog thisDialog = InsertContactDialog.this.getDialog();
                       if (thisDialog != null) {
                           thisDialog.cancel();
                       }
                       else {
                           Log.w(TAG, "Can't get the Dialog instance.");
                       }
                   }
               });

        // Create the AlertDialog object and return it
        AlertDialog alertDialog = builder.create();

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final AlertDialog alertDialog = (AlertDialog) dialog;

                // Disable the positive button. It will be enabled only when there is a valid email
                final Button button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (button != null) {
                    button.setEnabled(false);
                    button.setOnClickListener(new DontAutoCloseDialogListener(alertDialog));
                }
                else {
                    Log.w(TAG, "Can't get the dialog positive button.");
                }

                alertDialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });

        return alertDialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(EXTRA_NAME_ERROR, mNameErrorState);
        outState.putInt(EXTRA_EMAIL_ERROR, mEmailErrorState);
    }

    /**
     * Check the data inserted into the form and it all the required fields are present,
     * then it adds the contact into DB
     */
    private boolean checkAndInsert() {
        final Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Not attached to Activity: cannot insert contact in DB");
            return true;    // We can close, there's nothing to do anyway
        }
        String email = null;
        ArrayList<String> name = new ArrayList<String>(1), organization = new ArrayList<String>(1),
            title = new ArrayList<String>(1);

        if (mNameEditText != null && mNameEditText.getText() != null) {
            name.add(mNameEditText.getText().toString());
        }

        if (mEmailEditText != null && mEmailEditText.getText() != null) {
            email = mEmailEditText.getText().toString();
        }

        if (mOrganizationEditText != null && mOrganizationEditText.getText() != null &&
            TextUtils.isGraphic(mOrganizationEditText.getText())) {

            organization.add(mOrganizationEditText.getText().toString());
        }

        if (mTitleEditText != null && mTitleEditText.getText() != null &&
            TextUtils.isGraphic(mTitleEditText.getText())) {
            title.add(mTitleEditText.getText().toString());
        }

        if (activity instanceof MainActivity) {
            final MainActivity mainActivity = (MainActivity) activity;

            if (!isValidEmailAddress(email)) {
                // Empty or invalid email field!
                showEmailError(activity.getString(R.string.error_emailinput_invalid), 1);
                return false;
            }

            if (DataHelper.isEmailAlreadyPresent(mainActivity, email)) {
                // We add each email only once
                showEmailError(activity.getString(R.string.error_emailinput_duplicate), 2);
                return false;
            }

            if (DataHelper.insertContact(mainActivity, name, email, organization, title)) {
                mainActivity.showCroutonNao(activity.getString(R.string.new_contact_added, email), Style.CONFIRM);
                mainActivity.updateParticipantsCount();

                return true;
            }

            return false;
        }
        else {
            Log.e(TAG, "The parent Activity is not MainActivity! Wat is this I don't even");
            if (DEBUG) Log.d(TAG, "Activity class: " + activity.getLocalClassName());
            Toast.makeText(activity, activity.getString(R.string.insert_failed_wrong_parent), Toast.LENGTH_SHORT)
                 .show();
            return true;    // We can close, there's nothing to do anyway
        }
    }

    /**
     * Validates a string as an email address.
     *
     * @param email The email address to validate
     *
     * @return Returns true if the string is a valid email address,
     * false otherwise
     */
    private static boolean isValidEmailAddress(CharSequence email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email.toString()).matches();
    }

    /**
     * Validates a string as a name.
     *
     * @param name The name to validate
     *
     * @return Returns true if the string is a valid name,
     * false otherwise
     */
    private static boolean isValidName(CharSequence name) {
        return name != null && NAME_PATTERN.matcher(name.toString()).matches();
    }

    /**
     * Sets the validation state of the form.
     *
     * @param validated When true, the confirmation button will be enabled;
     *                  when false, it will be disabled.
     */
    private void setFormIsValidated(boolean validated) {
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);

            if (button != null) {
                button.setEnabled(validated);
            }
        }
    }

    /**
     * Validates the provided text as an email input from the user.
     *
     * @param text    The text to validate as email
     * @param context The current context
     */
    private void validateEmailInput(CharSequence text, Context context) {
        if (context != null) {
            if (!isValidEmailAddress(text)) {
                showEmailError(context.getString(R.string.error_emailinput_invalid), 1);
            }
            else if (DataHelper.isEmailAlreadyPresent(context, text.toString())) {
                showEmailError(context.getString(R.string.error_emailinput_duplicate), 2);
            }
            else {
                hideEmailError();
            }
        }
        else {
            hideEmailError();
        }
    }

    /**
     * Validates the provided text as a name input from the user.
     *
     * @param text The text to validate as name
     */
    private void validateNameInput(CharSequence text) {
        if (!isValidName(text)) {
            showNameError();
        }
        else {
            hideNameError();
        }
    }

    /**
     * Hides the email error textview.
     */
    private void hideEmailError() {
        mEmailErrorState = 0;

        // Re-enable the positive button of the dialog iif the name is also valid
        setFormIsValidated(mNameErrorState == 0);

        if (mEmailErrorTextView.getVisibility() == View.GONE) {
            return;         // No need to animate out the textview, it's already gone
        }

        AnimationSet fadeOutSet = new AnimationSet(true);
        fadeOutSet.addAnimation(new AlphaAnimation(1f, 0f));
        fadeOutSet.addAnimation(new TranslateAnimation(0f, 0f, 0f, -mErrorAnimTranslateY));
        fadeOutSet.setDuration(300);
        fadeOutSet.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // Don't care
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mEmailErrorTextView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // Don't care
            }
        });

        mEmailErrorTextView.startAnimation(fadeOutSet);
    }

    /**
     * Hides the name error textview.
     */
    private void hideNameError() {
        mNameErrorState = 0;

        // Re-enable the positive button of the dialog iif the name is also valid
        setFormIsValidated(mEmailErrorState == 0);

        if (mNameErrorTextView.getVisibility() == View.GONE) {
            return;         // No need to animate out the textview, it's already gone
        }

        AnimationSet fadeOutSet = new AnimationSet(true);
        fadeOutSet.addAnimation(new AlphaAnimation(1f, 0f));
        fadeOutSet.addAnimation(new TranslateAnimation(0f, 0f, 0f, -mErrorAnimTranslateY));
        fadeOutSet.setDuration(300);
        fadeOutSet.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // Don't care
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mNameErrorTextView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // Don't care
            }
        });

        mNameErrorTextView.startAnimation(fadeOutSet);
    }

    /**
     * Shows the email error textfield with the appropriate
     * error message.
     *
     * @param message The error message to show
     */
    private void showEmailError(CharSequence message, int errState) {
        mEmailErrorTextView.setText(message);
        mEmailErrorState = errState;

        // Disable the positive button of the dialog
        setFormIsValidated(false);

        if (mEmailErrorTextView.getVisibility() == View.VISIBLE) {
            return;         // No need to animate in the textview, it's already visible
        }

        mEmailErrorTextView.setVisibility(View.VISIBLE);

        AnimationSet fadeInSet = new AnimationSet(true);
        fadeInSet.addAnimation(new AlphaAnimation(0f, 1f));
        fadeInSet.addAnimation(new TranslateAnimation(0f, 0f, -mErrorAnimTranslateY, 0f));
        fadeInSet.setDuration(300);
        mEmailErrorTextView.startAnimation(fadeInSet);
    }

    /**
     * Shows the name error textfield with the appropriate
     * error message.
     */
    private void showNameError() {
        mNameErrorState = 1;

        // Disable the positive button of the dialog
        setFormIsValidated(false);

        if (mNameErrorTextView.getVisibility() == View.VISIBLE) {
            return;         // No need to animate in the textview, it's already visible
        }

        mNameErrorTextView.setVisibility(View.VISIBLE);

        AnimationSet fadeInSet = new AnimationSet(true);
        fadeInSet.addAnimation(new AlphaAnimation(0f, 1f));
        fadeInSet.addAnimation(new TranslateAnimation(0f, 0f, -mErrorAnimTranslateY, 0f));
        fadeInSet.setDuration(300);
        mNameErrorTextView.startAnimation(fadeInSet);
    }

    private class DontAutoCloseDialogListener implements View.OnClickListener {

        private final AlertDialog mDialog;

        private DontAutoCloseDialogListener(AlertDialog dialog) {
            mDialog = dialog;
        }

        @Override
        public void onClick(View v) {
            validateEmailInput(mEmailEditText.getText(), mDialog.getContext());
            validateNameInput(mNameEditText.getText());

            final Button button = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button != null && button.isEnabled()) {
                // Only proceed if the form is validated
                if (checkAndInsert()) {
                    mDialog.dismiss();
                }
            }
        }
    }
}
