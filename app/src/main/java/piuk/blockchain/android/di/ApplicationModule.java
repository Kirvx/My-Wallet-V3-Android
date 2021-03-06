package piuk.blockchain.android.di;

import android.app.Application;
import android.content.Context;

import info.blockchain.wallet.access.AccessState;
import info.blockchain.wallet.util.AppUtil;
import info.blockchain.wallet.util.PrefsUtil;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by adambennett on 08/08/2016.
 */

@Module
public class ApplicationModule {

    private final Application mApplication;

    public ApplicationModule(Application application) {
        mApplication = application;
    }

    @Provides
    @Singleton
    protected Context provideApplicationContext() {
        return mApplication;
    }

    @Provides
    @Singleton
    protected PrefsUtil providePrefsUtil() {
        return new PrefsUtil(mApplication);
    }

    @Provides
    @Singleton
    protected AppUtil provideAppUtil() {
        return new AppUtil(mApplication);
    }

    @Provides
    @Singleton
    protected AccessState provideAccessState() {
        return AccessState.getInstance();
    }
}
