package com.example.skyeos.data.di;

import android.content.Context;
import com.example.skyeos.data.db.LifeOsDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DataModule_Companion_ProvideDatabaseFactory implements Factory<LifeOsDatabase> {
  private final Provider<Context> contextProvider;

  public DataModule_Companion_ProvideDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LifeOsDatabase get() {
    return provideDatabase(contextProvider.get());
  }

  public static DataModule_Companion_ProvideDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new DataModule_Companion_ProvideDatabaseFactory(contextProvider);
  }

  public static LifeOsDatabase provideDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DataModule.Companion.provideDatabase(context));
  }
}
