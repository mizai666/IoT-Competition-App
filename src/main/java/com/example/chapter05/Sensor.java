package com.example.chapter05;

import java.io.Serializable;

public class Sensor implements Serializable {
    private String name;
    private String data;

    public Sensor(String name, String data) {
        this.name = name;
        this.data = data;
    }

    public Sensor() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}