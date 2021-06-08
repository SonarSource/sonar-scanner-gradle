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

package com.example.android.testing.blueprint.ui.espresso;


import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.example.android.testing.blueprint.HelloTestingBlueprintActivity;
import com.example.android.testing.blueprint.R;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class EspressoTest {

    @Rule
    public ActivityTestRule<HelloTestingBlueprintActivity> mActivityRule =
            new ActivityTestRule<>(HelloTestingBlueprintActivity.class);

    @Test
    public void findViewPerformActionAndCheckAssertion() {
        // Find Button and Click on it
        onView(withId(R.id.btn_hello_android_testing)).perform(click());

        // Find TextView and verify the correct text that is displayed
        onView(withId(R.id.text_view_rocks)).check(matches(withText(
                mActivityRule.getActivity().getString(R.string.android_testing_rocks))));
    }
}
