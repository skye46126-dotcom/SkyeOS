package com.example.skyeos.data.auth;

import android.content.Context;
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
public final class SQLiteCurrentUserContext_Factory implements Factory<SQLiteCurrentUserContext> {
  private final Provider<Context> contextProvider;

  private final Provider<LifeOsDatabase> databaseProvider;

  public SQLiteCurrentUserContext_Factory(Provider<Context> contextProvider,
      Provider<LifeOsDatabase> databaseProvider) {
    this.contextProvider = contextProvider;
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SQLiteCurrentUserContext get() {
    return newInstance(contextProvider.get(), databaseProvider.get());
  }

  public static SQLiteCurrentUserContext_Factory create(Provider<Context> contextProvider,
      Provider<LifeOsDatabase> databaseProvider) {
    return new SQLiteCurrentUserContext_Factory(contextProvider, databaseProvider);
  }

  public static SQLiteCurrentUserContext newInstance(Context context, LifeOsDatabase database) {
    return new SQLiteCurrentUserContext(context, database);
  }
}
