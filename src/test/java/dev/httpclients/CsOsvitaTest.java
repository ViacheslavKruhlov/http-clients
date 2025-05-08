package dev.httpclients;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;

public class CsOsvitaTest {

    @Test
    public void test() {
        char[] ints = "00011".toCharArray();

        int l = 1;
        int r = ints.length - 1;
        int size = ints.length / 2 + 1;

        while (l < size) {
            if (ints[l] == '1') {
                System.out.println(l - 1);
                break;
            } else if (ints[r - l] == '0') {
                System.out.println(r - l);
                break;
            }

            l++;
        }
    }
}