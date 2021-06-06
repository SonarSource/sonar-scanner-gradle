/*
 * Copyright 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.testing.blueprint.integration;

import android.content.Context;
import android.os.Bundle;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import com.example.android.testing.blueprint.R;
import com.example.android.testing.blueprint.Readiness;
import com.example.android.testing.blueprint.androidlibrarymodule.AndroidLibraryModuleClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Android test to showcase the usage of an Android Library from the main app module.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class AndroidLibraryModuleIntegrationTest {

  private Context mContext;

  /**
   * This test class showcases passing arguments from the command line to the instrumentation.
   * <p>
   * Every @Test will fail if the argument "argument1" has the value "make_test_fail". See README
   * for more information.
   */
  @Before
  public void checkCustomArgument() {
    // Get the arguments bundle.
    Bundle arguments = InstrumentationRegistry.getArguments();

    // Get the value if "argument1" exists
    String argument1 = arguments.getString("argument1");

    // Do something with the value. In this example it will make the test fail but it can be
    // used to modify a value in SharedPreferences or set the hostname of a backend server,
    // for example.
    assertThat(argument1, not(equalTo("make_test_fail")));
  }

  @Before
  public void initTargetContext() {
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertThat(mContext, notNullValue());
  }

  @Test
  public void verifyResourceFromLibrary() {
    assertThat(mContext.getString(R.string.library_module_hello_world),
      is(equalTo("Hello from an Android library module!")));
  }

  @Test
  public void verifyClassFromLibrary() {
    AndroidLibraryModuleClass libraryModuleInstance = new AndroidLibraryModuleClass();
    assertThat(libraryModuleInstance.isReady(), is(true));
  }

  @Test
  public void verifyResourceFromFeature() {
    String actual =
      mContext.getString(
        mContext.getResources()
          .getIdentifier("feature_module_hello_world", "string", "com.example.android.testing.blueprint.module_android_feature"));

    assertThat(actual, is(equalTo("Hello from an Android feature module!")));
  }

  @Test
  public void verifyClassFromFeature() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    Readiness featureModuleInstance = (Readiness) Class.forName("com.example.android.testing.blueprint.androidfeaturemodule.AndroidFeatureModuleClass").newInstance();
    assertThat(featureModuleInstance.isReady(), is(true));
  }
}