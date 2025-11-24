import cn.redture.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class UlidTest {

    @Test
    public void testUlidGeneration() {
        log.debug(IdUtil.nextId());
    }
}
