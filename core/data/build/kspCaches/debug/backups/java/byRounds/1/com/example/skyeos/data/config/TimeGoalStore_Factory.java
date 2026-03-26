package com.example.skyeos.data.config;

import android.content.Context;
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
public final class TimeGoalStore_Factory implements Factory<TimeGoalStore> {
  private final Provider<Context> contextProvider;

  public TimeGoalStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public TimeGoalStore get() {
    return newInstance(contextProvider.get());
  }

  public static TimeGoalStore_Factory create(Provider<Context> contextProvider) {
    return new TimeGoalStore_Factory(contextProvider);
  }

  public static TimeGoalStore newInstance(Context context) {
    return new TimeGoalStore(context);
  }
}
