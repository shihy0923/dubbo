package com.tuling;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.List;

public class SpiTest {
    public static void main(String[] args) {

//        ServiceLoader<Car> cars = ServiceLoader.load(Car.class);
//        for (Car car : cars) {
//            System.out.println(car.getCarName(null));
//        }


//        ExtensionLoader<Protocol> extensionLoader = ExtensionLoader.getExtensionLoader(Protocol.class);
//        Protocol protocol = extensionLoader.getExtension("http");
//        System.out.println(protocol);

//        ExtensionLoader<Car> extensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
//
//        Car car = extensionLoader.getExtension("true"); // 自动注入，AOP
//
//        System.out.println(car.getCarName());

        //*************************getExtension方法*******************************
        ExtensionLoader<Person> extensionLoader1 = ExtensionLoader.getExtensionLoader(Person.class);
        Person person = extensionLoader1.getExtension("black");  // BlackPerson

        URL url1 = new URL("x", "localhost", 8080);
        url1 = url1.addParameter("car", "black");

        System.out.println(person.getCar().getCarName(url1));


        //*************************getActivateExtension方法*******************************
        ExtensionLoader<Person> extensionLoader2 = ExtensionLoader.getExtensionLoader(Person.class);

        URL url2 = new URL("x", "localhost", 8080);

        url2 = url2.addParameter("person", "black");

        List<Person> test = extensionLoader2.getActivateExtension(url2, "person");

        test.stream().forEach(t-> System.out.println(t.toString()));


//        ExtensionLoader<Filter> extensionLoader = ExtensionLoader.getExtensionLoader(Filter.class);
//        URL url = new URL("http://", "localhost", 8080);
//        url = url.addParameter("cache", "test");
//        List<Filter> activateExtensions = extensionLoader.getActivateExtension(url, new String[]{"validation"}, CommonConstants.CONSUMER);
//        for (Filter activateExtension : activateExtensions) {
//            System.out.println(activateExtension);
//        }


//        ConcurrentHashSet set = new ConcurrentHashSet();
//        set.add("周瑜1");
//        set.add("周瑜2");
//        set.add("周瑜3");
//
//        System.out.println(set);
    }
}
