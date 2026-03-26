package com.example.skyeos.data.repository;

import android.content.Context;
import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class SQLiteLifeOsBackupRepository_Factory implements Factory<SQLiteLifeOsBackupRepository> {
  private final Provider<Context> contextProvider;

  private final Provider<LifeOsDatabase> databaseProvider;

  private final Provider<CurrentUserContext> userContextProvider;

  public SQLiteLifeOsBackupRepository_Factory(Provider<Context> contextProvider,
      Provider<LifeOsDatabase> databaseProvider, Provider<CurrentUserContext> userContextProvider) {
    this.contextProvider = contextProvider;
    this.databaseProvider = databaseProvider;
    this.userContextProvider = userContextProvider;
  }

  @Override
  public SQLiteLifeOsBackupRepository get() {
    return newInstance(contextProvider.get(), databaseProvider.get(), userContextProvider.get());
  }

  public static SQLiteLifeOsBackupRepository_Factory create(Provider<Context> contextProvider,
      Provider<LifeOsDatabase> databaseProvider, Provider<CurrentUserContext> userContextProvider) {
    return new SQLiteLifeOsBackupRepository_Factory(contextProvider, databaseProvider, userContextProvider);
  }

  public static SQLiteLifeOsBackupRepository newInstance(Context context, LifeOsDatabase database,
      CurrentUserContext userContext) {
    return new SQLiteLifeOsBackupRepository(context, database, userContext);
  }
}
