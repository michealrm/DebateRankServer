package io.micheal.nametokenizer;

import static io.micheal.nametokenizer.Name.*;
import java.util.HashMap;

public class Tokenizer {

    public static HashMap<Name, String> tokenize(String name) {
        return tokenize(name, LAST);
    }

    public static HashMap<Name, String> tokenize(String name, Name priority) {
        HashMap<Name, String> tokenized = new HashMap<Name, String>();
        if(name == null || name.equals(""))
            return tokenized;
        String[] split = name.split("\\s+");
        if(split.length == 1)
            tokenized.put(priority, split[0]);
        else if(split.length == 2) {
            tokenized.put(FIRST, split[0]);
            tokenized.put(LAST, split[1]);
        }
        else if(split.length == 3) {
            if(split[2].contains("Jr|Junior|III|IV|V|VI|VII|VIII|IX|X")) {
                tokenized.put(FIRST, split[0]);
                tokenized.put(LAST, split[1]);
                tokenized.put(SURNAME, split[2]);
            }
            else {
                tokenized.put(FIRST, split[0]);
                tokenized.put(MIDDLE, split[1]);
                tokenized.put(LAST, split[2]);
            }
        }
        else if(split.length == 4) {
            if(split[3].contains("Jr|Junior|III|IV|V|VI|VII|VIII|IX|X")) {
                tokenized.put(FIRST, split[0]);
                tokenized.put(MIDDLE, split[1]);
                tokenized.put(LAST, split[2]);
                tokenized.put(SURNAME, split[3]);
            }
        }
        else if(split.length > 4) {
        	tokenized.put(FIRST, split[0]);
        	for(int i = 1;i<split.length;i++) {
        		
        	}
        }
        return null;
    }

}
