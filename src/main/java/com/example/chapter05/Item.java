package com.example.chapter05;

import java.io.Serializable;
import java.util.Arrays;

public class Item implements Serializable {
    private String projectID;
    private Sensor[] sensors;

    public Item(String projectID) {
        this.projectID = projectID;
        this.sensors = new Sensor[0];   // 初始无传感器
    }

    public String getProjectID() {
        return projectID;
    }

    public Sensor[] getSensors() {
        return sensors;
    }

    public void setSensors(Sensor[] sensors) {
        this.sensors = sensors;
    }

    // 添加一个传感器（方便后面使用）
    public void addSensor(Sensor sensor) {
        Sensor[] newSensors = Arrays.copyOf(sensors, sensors.length + 1);
        newSensors[sensors.length] = sensor;
        sensors = newSensors;
    }
}