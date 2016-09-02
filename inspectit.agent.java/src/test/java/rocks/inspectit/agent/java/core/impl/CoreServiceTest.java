package rocks.inspectit.agent.java.core.impl;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledExecutorService;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import rocks.inspectit.agent.java.core.IPlatformManager;
import rocks.inspectit.shared.all.communication.DefaultData;
import rocks.inspectit.shared.all.testbase.TestBase;

@SuppressWarnings({ "PMD" })
public class CoreServiceTest extends TestBase {

	@InjectMocks
	CoreService coreService;

	@Mock
	Logger log;

	@Mock
	DefaultDataHandler defaultDataHandler;

	@Mock
	IPlatformManager platformManager;

	@Mock
	ScheduledExecutorService executorService;

	public static class AddDefaultData extends CoreServiceTest {

		@BeforeMethod
		public void start() {
			coreService.start();
		}

		@Test
		public void add() throws InterruptedException {
			DefaultData defaultData = mock(DefaultData.class);

			coreService.addDefaultData(defaultData);

			// need to sleep a bit so handler is notified
			Thread.sleep(100);

			ArgumentCaptor<DefaultDataWrapper> captor = ArgumentCaptor.forClass(DefaultDataWrapper.class);
			verify(defaultDataHandler).onEvent(captor.capture(), anyLong(), eq(true));
		}

		@Test
		public void noAddOnShutdown() throws InterruptedException {
			DefaultData defaultData = mock(DefaultData.class);
			coreService.stop();
		}


		@AfterMethod
		public void stop() {
			coreService.stop();
		}

	}

}
