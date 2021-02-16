package com.tuling;

import org.apache.dubbo.common.extension.Activate;

/**
 * @ClassName: WhitePerson.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2021年02月15日
 */
@Activate(value = {"person"})
public class WhitePerson implements Person {

    private Car car;   // Adaptive(代理)

    public void setCar(Car car) {
        this.car = car;
    }

    @Override
    public Car getCar() {
        return car;
    }
}