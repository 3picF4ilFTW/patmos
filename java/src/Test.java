
import org.antlr.runtime.*;

public class Test {
    public static void main(String[] args) throws Exception {
        ANTLRInputStream input = new ANTLRInputStream(System.in);
        PatGramLexer lexer = new PatGramLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PatGramParser parser = new PatGramParser(tokens);
        parser.prog();
    }
}

