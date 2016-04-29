package ge.mygpi.karaoke;

import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

/**
 * Created by luka on 4/29/16.
 */
public class LinkedAlertDialog {

    public static AlertDialog create(Context context, String dialogTitle, String dismissLabel, String messageText) {
        final TextView message = new TextView(context);
        final SpannableString s =
                new SpannableString(messageText);
        Linkify.addLinks(s, Linkify.WEB_URLS);
        message.setText(s);
        message.setMovementMethod(LinkMovementMethod.getInstance());

        return new AlertDialog.Builder(context)
                .setTitle(dialogTitle)
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(dismissLabel, null)
                .setView(message)
                .create();
    }
}