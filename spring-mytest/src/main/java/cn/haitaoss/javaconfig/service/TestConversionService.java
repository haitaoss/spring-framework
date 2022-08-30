package cn.haitaoss.javaconfig.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;

@Component
public class TestConversionService {
    @Value("2022-08-11")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date = new Date();

    @Value("101.11")
    @NumberFormat(pattern = "#")
    private Integer money;

    @Value("code,play")
    private String[] jobs;

    @Value("haitaoss")
    private Person person;

    @Override
    public String toString() {
        return "TestConversionService{" + "date=" + date + ", money=" + money + ", jobs=" + Arrays.toString(jobs)
               + ", person=" + person + '}';
    }
}

