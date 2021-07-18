import net.debaterank.server.models.Debater;
import org.junit.Test;
import static org.junit.Assert.*;

import static net.debaterank.server.util.DRHelper.*;

public class DRHelperTest {

    class ObjEmpty {}
    class Obj1 {
        String name;
        int age;

        Obj1(String n, int a) {
            name = n;
            age = a;
        }
    }

    @Test
    public void testReplaceNull() {
        // empty object
        ObjEmpty oe1 = new ObjEmpty();
        ObjEmpty oe2 = new ObjEmpty();
        replaceNull(oe1, oe2);

        // different type
        oe1 = new ObjEmpty();
        Obj1 ob1 = new Obj1("name", 2);
        int oldOE1HC = oe1.hashCode();
        int oldOB1HC = ob1.hashCode();
        replaceNull(oe1, ob1);
        assertEquals(oldOE1HC, oe1.hashCode());
        assertEquals(oldOB1HC, ob1.hashCode());

        // one null
        Debater d1 = new Debater("a", "g", "a", null, null);
        Debater d2 = new Debater("b", null, "b", null, null);
        assertEquals("g", d1.getMiddle());
        assertNull(d2.getMiddle());
        replaceNull(d1, d2);
        assertEquals("g", d2.getMiddle());
        assertEquals("b", d2.getFirst());
        assertEquals("a", d1.getFirst());
        assertEquals("g", d1.getMiddle());

        // both null
        ob1 = null;
        Obj1 ob2 = null;
        replaceNull(ob1, ob2);
    }

}
