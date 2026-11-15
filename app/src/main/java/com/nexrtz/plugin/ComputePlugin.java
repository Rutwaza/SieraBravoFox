// Save this as ComputePlugin.java
package com.nexrtz.plugin;

public class ComputePlugin {
    public String start(String inputData) {
        // Safe simulation logic: Perform an operation and format a log string
        int inputVal = Integer.parseInt(inputData);
        int result = inputVal * 42;
        return "Payload Executed Successfully. Resulting Token: " + result;
    }
}
