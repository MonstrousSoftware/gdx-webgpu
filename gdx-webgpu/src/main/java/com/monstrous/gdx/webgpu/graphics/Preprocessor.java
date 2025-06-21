/*******************************************************************************
 * Copyright 2025 Monstrous Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.webgpu.graphics;

import java.util.HashMap;
import java.util.Map;

/**
 * Very basic preprocessor for shader code.
 * Supports:
 *      #define NAME  (not #define name value!)
 *      #ifdef / #ifndef
 *      #else
 *      #endif
 *
 */

public class Preprocessor {

    private static final Map<String,String> defineMap = new HashMap<>();

    public static String process(String input){
        defineMap.clear();
        StringBuilder output = new StringBuilder();
        int nestDepth = 0;
        int ignoreLevel = 0;

        String[] lines = input.split("\n");
        for(String line : lines){
            String trimmed = line.trim();

            if(trimmed.startsWith("#ifdef")){
                nestDepth++;
                if(ignoreLevel == 0) {
                    String[] words = trimmed.split("[ \t]");
                    if(!defined(words[1]))
                        ignoreLevel = nestDepth;
                }
            } else if(trimmed.startsWith("#ifndef")){
                nestDepth++;
                if(ignoreLevel == 0) {
                    String[] words = trimmed.split("[ \t]");
                    if(defined(words[1]))
                        ignoreLevel = nestDepth;
                }
            } else if(trimmed.startsWith("#else")){
                if (ignoreLevel == nestDepth)
                    ignoreLevel = 0;
                else if (ignoreLevel == 0)
                    ignoreLevel = nestDepth;

            } else if(trimmed.startsWith("#endif")){
                if(ignoreLevel == nestDepth)
                    ignoreLevel = 0;
                nestDepth--;
            }
            else if(ignoreLevel == 0) {
                if (trimmed.startsWith("#define")) {
                    String[] words = trimmed.split("[ \t]");
                    if(words.length == 2)
                        define(words[1], null);
                    else
                        define(words[1], words[2]);
                }
                else
                    output.append(line).append('\n');

            }
            //output.append(nestDepth).append(':').append(branchTaken).append(line).append('\n');

        }
        return output.toString();
    }

    // b may be null, e.g. #define DEBUG
    private static void define(String a, String b){
        defineMap.put(a, b);
        //System.out.print("#define "+a);
        if(b != null)
            System.out.print(" := "+b);
    }

    private static boolean defined(String name){
        return defineMap.containsKey(name);
    }

    private static boolean evaluate(String expr){
        return expr.contentEquals("true");      // placeholder implementation
    }
}
