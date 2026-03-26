package com.example.skyeos.data.repository;

import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
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
public final class SQLiteLifeOsReviewRepository_Factory implements Factory<SQLiteLifeOsReviewRepository> {
  private final Provider<LifeOsDatabase> databaseProvider;

  private final Provider<CurrentUserContext> userContextProvider;

  public SQLiteLifeOsReviewRepository_Factory(Provider<LifeOsDatabase> databaseProvider,
      Provider<CurrentUserContext> userContextProvider) {
    this.databaseProvider = databaseProvider;
    this.userContextProvider = userContextProvider;
  }

  @Override
  public SQLiteLifeOsReviewRepository get() {
    return newInstance(databaseProvider.get(), userContextProvider.get());
  }

  public static SQLiteLifeOsReviewRepository_Factory create(
      Provider<LifeOsDatabase> databaseProvider, Provider<CurrentUserContext> userContextProvider) {
    return new SQLiteLifeOsReviewRepository_Factory(databaseProvider, userContextProvider);
  }

  public static SQLiteLifeOsReviewRepository newInstance(LifeOsDatabase database,
      CurrentUserContext userContext) {
    return new SQLiteLifeOsReviewRepository(database, userContext);
  }
}
