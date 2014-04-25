package com.d2dx;

public class TestMain
{
    
    private int foo;

    public TestMain()
    {
        this.foo = 42;
    }
    
    public static void main(String[] args)
    {
        System.out.println(new TestMain().foo);
    }
}
