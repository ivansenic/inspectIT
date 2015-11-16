package info.novatec.inspectit.util;

import info.novatec.inspectit.spring.logger.LoggerPostProcessor;
import info.novatec.inspectit.storage.nio.ByteBufferProvider;
import info.novatec.inspectit.storage.nio.stream.ExtendedByteBufferOutputStream;
import info.novatec.inspectit.storage.nio.stream.SocketExtendedByteBufferInputStream;
import info.novatec.inspectit.storage.nio.stream.StreamProvider;
import info.novatec.inspectit.storage.serializer.ISerializerProvider;
import info.novatec.inspectit.storage.serializer.impl.SerializationManager;
import info.novatec.inspectit.storage.serializer.schema.ClassSchemaManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.core.io.ClassPathResource;

/**
 * Stream and serialization manager provider for the non-Spring enabled use.
 * 
 * @author Ivan Senic
 * 
 */
public class PrototypeProvider extends StreamProvider implements ISerializerProvider<SerializationManager> {

	/**
	 * Logger post processor.
	 */
	private static final LoggerPostProcessor LOGGER_POST_PROCESSOR = new LoggerPostProcessor();

	/**
	 * {@link KryoNetNetwork} as singleton.
	 */
	private KryoNetNetwork kryoNetNetwork;

	/**
	 * {@link ClassSchemaManager} as singleton.
	 */
	private ClassSchemaManager classSchemaManager;

	/**
	 * {@link ByteBufferProvider} for the buffers.
	 */
	private ByteBufferProvider byteBufferProvider;

	/**
	 * Executor service.
	 */
	private ExecutorService executorService;

	/**
	 * If initialization error occurs.
	 * 
	 * @throws Exception
	 *             Exception.
	 */
	public void init() throws Exception {
		kryoNetNetwork = new KryoNetNetwork();
		executorService = Executors.newFixedThreadPool(1);

		classSchemaManager = new ClassSchemaManager();
		classSchemaManager.setSchemaListFile(new ClassPathResource("schema/schemaList.txt"));

		byteBufferProvider = new ByteBufferProvider();
		// TODO Vivek set proper values for Andriod environment
		byteBufferProvider.setBufferSize(64 * 1024); // 64 Kb
		byteBufferProvider.setPoolMinCapacity(512 * 1024); // 512 Kb
		byteBufferProvider.setPoolMaxCapacity(4 * 1024 * 1024); // 4 Mb
		byteBufferProvider.setBufferPoolMaxDirectMemoryOccupancy(0.3f); // 30%
		byteBufferProvider.setBufferPoolMaxDirectMemoryOccupancy(0.7f); // 70%

		addLogger(classSchemaManager, byteBufferProvider);

		classSchemaManager.afterPropertiesSet();
		byteBufferProvider.afterPropertiesSet();
	}

	/**
	 * {@inheritDoc}
	 */
	public SerializationManager createSerializer() {
		try {
			SerializationManager manager = new SerializationManager();
			manager.setKryoNetNetwork(kryoNetNetwork);
			manager.setSchemaManager(classSchemaManager);
			manager.afterPropertiesSet();
			return manager;
		} catch (Exception e) {
			throw new RuntimeException("Can not initialize serialization manager.", e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ExtendedByteBufferOutputStream createExtendedByteBufferOutputStream() {
		ExtendedByteBufferOutputStream stream = new ExtendedByteBufferOutputStream();
		stream.setByteBufferProvider(byteBufferProvider);
		return stream;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected SocketExtendedByteBufferInputStream createSocketExtendedByteBufferInputStream() {
		SocketExtendedByteBufferInputStream stream = new SocketExtendedByteBufferInputStream();
		stream.setByteBufferProvider(byteBufferProvider);
		stream.setExecutorService(executorService);
		return stream;
	}

	/**
	 * Adds logger via reflection.
	 * 
	 * @param beans
	 *            Beans to add logger to.
	 */
	private void addLogger(Object... beans) {
		for (Object bean : beans) {
			LOGGER_POST_PROCESSOR.postProcessBeforeInitialization(bean, "");
		}
	}

}
