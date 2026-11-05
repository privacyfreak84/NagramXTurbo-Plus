package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Outline;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.DatacenterActivity;

public class NekoAboutActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_LOGO_HEADER = 1000;

    private int logoHeaderRow;
    private int checkUpdateRow;
    private int releasesRow;
    private int sourceCodeRow;
    private int upstreamRow;
    private int versionRow;
    private int architectureRow;
    private int datacenterStatusRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        logoHeaderRow = addRow();
        checkUpdateRow = addRow();
        releasesRow = addRow();
        sourceCodeRow = addRow();
        upstreamRow = addRow();
        versionRow = addRow();
        architectureRow = addRow();
        datacenterStatusRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.About);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == checkUpdateRow) {
            Browser.openUrl(getParentActivity(), "tg://update");
        } else if (position == releasesRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/privacyfreak84/NagramXTurbo-Plus/releases");
        } else if (position == sourceCodeRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/privacyfreak84/NagramXTurbo-Plus");
        } else if (position == upstreamRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/temporaryna/NagramXTurbo");
        } else if (position == datacenterStatusRow) {
            presentFragment(new DatacenterActivity(0));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_LOGO_HEADER) {
                FrameLayout logoHeaderCell = new FrameLayout(mContext);
                logoHeaderCell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                logoHeaderCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                ImageView logoImageView = new ImageView(mContext);
                logoImageView.setImageResource(R.drawable.turbo_logo);
                logoImageView.setClipToOutline(true);
                logoImageView.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() / 2f);
                    }
                });
                logoHeaderCell.addView(logoImageView, LayoutHelper.createFrame(90, 90, Gravity.CENTER_HORIZONTAL, 0, 15, 0, 0));
                TextView nameTextView = new TextView(mContext);
                nameTextView.setText(getString(R.string.TurboPlusName));
                nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
                nameTextView.setTypeface(AndroidUtilities.bold());
                nameTextView.setGravity(Gravity.CENTER);
                nameTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                logoHeaderCell.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 115, 16, 0));
                TextView taglineTextView = new TextView(mContext);
                taglineTextView.setText(getString(R.string.TurboPlusTagline));
                taglineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                taglineTextView.setGravity(Gravity.CENTER);
                taglineTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                logoHeaderCell.addView(taglineTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 146, 16, 16));
                return new RecyclerListView.Holder(logoHeaderCell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.getItemViewType() == TYPE_SETTINGS) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (position == checkUpdateRow) {
                    textCell.setText(getString(R.string.CheckUpdate), true);
                } else if (position == releasesRow) {
                    textCell.setTextAndValue(getString(R.string.TurboReleases), "GitHub", true);
                } else if (position == sourceCodeRow) {
                    textCell.setTextAndValue(getString(R.string.SourceCode), "GitHub", true);
                } else if (position == upstreamRow) {
                    textCell.setTextAndValue(getString(R.string.TurboUpstream), "temporaryna/NagramXTurbo", true);
                } else if (position == versionRow) {
                    String commit = BuildConfig.GIT_COMMIT;
                    String version = BuildVars.BUILD_VERSION_STRING + (commit != null && commit.length() >= 7 ? " (" + commit.substring(0, 7) + ")" : "");
                    textCell.setTextAndValue(getString(R.string.TurboVersion), version, true);
                } else if (position == architectureRow) {
                    textCell.setTextAndValue(getString(R.string.TurboArchitecture), android.os.Build.SUPPORTED_ABIS[0].toLowerCase(java.util.Locale.ROOT), true);
                } else if (position == datacenterStatusRow) {
                    textCell.setText(getString(R.string.DatacenterStatus), false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == logoHeaderRow) {
                return TYPE_LOGO_HEADER;
            }
            return TYPE_SETTINGS;
        }
    }
}
