/*
 * Copyright 2017 Thomas Rix.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.marandus.pciid.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.github.marandus.pciid.model.Device;
import com.github.marandus.pciid.model.DeviceClass;
import com.github.marandus.pciid.model.DeviceSubclass;
import com.github.marandus.pciid.model.ProgramInterface;
import com.github.marandus.pciid.model.Subsystem;
import com.github.marandus.pciid.model.Vendor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Main entry point into the PCI IDs database library.
 *
 * @author Thomas Rix (thomasrix@exodus-project.net)
 * @since 0.1
 *
 * @see <a href="https://pci-ids.ucw.cz/">The PCI ID Repository</a>
 */
@Slf4j
public class PciIdsDatabase {

    /**
     * Default URI for remote pci-ids file
     */
    public final String PCI_IDS_URL = "https://pci-ids.ucw.cz/v2.2/pci.ids";

    @Getter
    private boolean ready = false;

    private final Map<String, Vendor> vendorDatabase;
    private final Map<String, DeviceClass> deviceClassDatabase;
    private final DatabaseFileParser parser;

    /**
     * Create a new empty database
     */
    public PciIdsDatabase() {
        this.vendorDatabase = new TreeMap<>();
        this.deviceClassDatabase = new TreeMap<>();
        this.parser = new DatabaseFileParser();
    }

    /**
     * Load the pci.ids file from the default remote location. The default URI is defined in
     * {@link PciIdsDatabase#PCI_IDS_URL}. Any existing database contents will be removed before
     * loading the new database file. During the load process the database will not be accessible.
     *
     * @throws IOException if the database file cannot be read, is malformed or otherwise
     * unprocessable
     * @since 0.1
     */
    public synchronized void loadRemote() throws IOException {
        URI uri = URI.create(this.PCI_IDS_URL);

        this._loadRemote(uri);
    }

    /**
     * Load the pci.ids file from the specified remote location. Any existing database contents will
     * be removed before loading the new database file. During the load process the database will
     * not be accessible.
     *
     * @param uri Remote location of the pci.ids file
     * @throws IOException if the database file cannot be read, is malformed or otherwise
     * unprocessable
     * @since 0.1
     */
    public synchronized void loadRemote(URI uri) throws IOException {
        this._loadRemote(uri);
    }

    /**
     * Load the pci.ids file from the provided input stream. Any existing database contents will be
     * removed before loading the new database file. During the load process the database will not
     * be accessible.
     *
     * @param is Input stream holding the database file contents
     * @throws IOException if the database file cannot be read, is malformed or otherwise
     * unprocessable
     * @since 0.1
     */
    public synchronized void loadStream(InputStream is) throws IOException {
        this._loadStream(is);
    }

    /**
     * Retrieve a list of all vendors found in the database. If the database is empty, the returned
     * list is empty as well.
     *
     * @return List of vendors
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.1
     */
    public List<Vendor> findAllVendors() {
        if (this.ready) {
            List<Vendor> rv = this.vendorDatabase.values().stream()
                    .sorted()
                    .collect(Collectors.toList());

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a specific vendor from the database. If the vendor ID does not exist, the return
     * value is <tt>null</tt>.
     *
     * @param vendorId Vendor ID to search for
     * @return Requested vendor object or null
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.1
     */
    public Vendor findVendor(String vendorId) {
        if (this.ready) {
            Vendor rv = this.vendorDatabase.get(vendorId);

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a list of all known devices for a specific vendor. If the vendor does not exist or
     * no devices are known for this vendor, the returned list is empty.
     *
     * @param vendorId Vendor ID to search for
     * @return List of vendor's devices
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.1
     */
    public List<Device> findAllDevices(final String vendorId) {
        if (this.ready) {
            Vendor v = this.vendorDatabase.get(vendorId);
            if (v == null) {
                return new ArrayList<>(0);
            }

            List<Device> rv = v.getDevices().values().stream()
                    .sorted()
                    .collect(Collectors.toList());

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a specific device for a specific vendor. If the vendor or device does not exist, the
     * return value is <tt>null</tt>.
     *
     * @param vendorId Vendor ID to search for
     * @param deviceId Device ID to search for
     * @return Requested device object or null
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.1
     */
    public Device findDevice(final String vendorId, final String deviceId) {
        if (this.ready) {
            Vendor v = this.vendorDatabase.get(vendorId);
            if (v == null) {
                return null;
            }

            Device rv = v.getDevices().get(deviceId);

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a list of all known subsystems for a specific device. If the vendor or device does
     * not exist or no subsystems are known for this device, the returned list is empty. The result
     * list is sorted by subvendor ID and subsystem ID.
     *
     * @param vendorId Vendor ID to search for
     * @param deviceId Device ID to search for
     * @return List of device's subsystems
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.1
     */
    public List<Subsystem> findAllSubsystems(final String vendorId, final String deviceId) {
        if (this.ready) {
            Vendor v = this.vendorDatabase.get(vendorId);
            if (v == null) {
                return new ArrayList<>(0);
            }

            Device d = v.getDevices().get(deviceId);
            if (d == null) {
                return new ArrayList<>(0);
            }

            List<Subsystem> rv = d.getSubsystems().stream()
                    .sorted()
                    .collect(Collectors.toList());

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a list of all known subsystems for a specific device that share a specific vendor.
     * If the vendor, device, or subvendor does not exist or no subsystems are known for this
     * device, the returned list is empty. The result list is sorted by subvendor ID and subsystem
     * ID.
     *
     * @param vendorId Vendor ID to search for
     * @param deviceId Device ID to search for
     * @param subvendorId Subsystem vendor ID to search for
     * @return List of device's subsystems
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.1
     */
    public List<Subsystem> findAllSubsystemsWithVendor(final String vendorId, final String deviceId, final String subvendorId) {
        if (this.ready) {
            Vendor v = this.vendorDatabase.get(vendorId);
            if (v == null) {
                return new ArrayList<>(0);
            }

            Device d = v.getDevices().get(deviceId);
            if (d == null) {
                return new ArrayList<>(0);
            }

            List<Subsystem> rv = d.getSubsystems().stream()
                    .filter(s -> s.getVendorId().equals(subvendorId))
                    .sorted()
                    .collect(Collectors.toList());

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a list of all device classes found in the database. If the database is empty, the
     * returned list is empty as well.
     *
     * @return List of device classes
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.3
     */
    public List<DeviceClass> findAllDeviceClasses() {
        if (this.ready) {
            List<DeviceClass> rv = this.deviceClassDatabase.values().stream()
                    .sorted()
                    .collect(Collectors.toList());

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a specific device class from the database. If the vendor ID does not exist, the
     * return value is <tt>null</tt>.
     *
     * @param classId Device class ID to search for
     * @return Requested device class object or null
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.3
     */
    public DeviceClass findDeviceClass(String classId) {
        if (this.ready) {
            DeviceClass rv = this.deviceClassDatabase.get(classId);

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a list of all known subclasses for a specific device class. If the device class does
     * not exist or no subclasses are known for this device class, the returned list is empty.
     *
     * @param classId Device class ID to search for
     * @return List of device class' subclasses
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.3
     */
    public List<DeviceSubclass> findAllDeviceSubclasses(String classId) {
        if (this.ready) {
            DeviceClass d = this.deviceClassDatabase.get(classId);
            if (d == null) {
                return new ArrayList<>(0);
            }

            List<DeviceSubclass> rv = d.getSubclasses().values().stream()
                    .sorted()
                    .collect(Collectors.toList());

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a specific subclass for a specific device class. If the device class or subclass
     * does not exist, the return value is <tt>null</tt>.
     *
     * @param classId Device class ID to search for
     * @param subclassId Subclass ID to search for
     * @return Requested subclass object or null
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.3
     */
    public DeviceSubclass findDeviceSubclass(String classId, String subclassId) {
        if (this.ready) {
            DeviceClass d = this.deviceClassDatabase.get(classId);
            if (d == null) {
                return null;
            }

            DeviceSubclass rv = d.getSubclasses().get(subclassId);

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a list of all known program interfaces for a specific device subclass. If the device
     * subclass does not exist or no program interfaces are known for this device subclass, the
     * returned list is empty.
     *
     * @param classId Device class ID to search for
     * @param subclassId Subclass ID to search for
     * @return List of device subclass' program interfaces
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.3
     */
    public List<ProgramInterface> findAllProgramInterfaces(String classId, String subclassId) {
        if (this.ready) {
            DeviceClass d = this.deviceClassDatabase.get(classId);
            if (d == null) {
                return new ArrayList<>(0);
            }

            DeviceSubclass s = d.getSubclasses().get(subclassId);
            if (s == null) {
                return new ArrayList<>(0);
            }

            List<ProgramInterface> rv = s.getProgramInterfaces().values().stream()
                    .sorted()
                    .collect(Collectors.toList());

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Retrieve a specific program interface for a specific device subclass. If the device class,
     * subclass, or program interface does not exist, the return value is <tt>null</tt>.
     *
     * @param classId Device class ID to search for
     * @param subclassId Subclass ID to search for
     * @param ifaceId Program interface ID to search for
     * @return Requested program interface object or null
     * @throws IllegalStateException if database is not ready, i.e. no database file was loaded
     * @since 0.3
     */
    public ProgramInterface findProgramInterface(String classId, String subclassId, String ifaceId) {
        if (this.ready) {
            DeviceClass d = this.deviceClassDatabase.get(classId);
            if (d == null) {
                return null;
            }

            DeviceSubclass s = d.getSubclasses().get(subclassId);
            if (s == null) {
                return null;
            }

            ProgramInterface rv = s.getProgramInterfaces().get(ifaceId);

            return rv;
        }

        throw new IllegalStateException("Database not ready");
    }

    /**
     * Load the pci.ids file from the specified remote location. Unsynchronized function for
     * internal use only.
     *
     * @param uri Remote location of the pci.ids file
     * @throws IOException if the database file cannot be read, is malformed or otherwise
     * unprocessable
     */
    private void _loadRemote(URI uri) throws IOException {
        ArgumentValidator.requireNonNull(uri, "PCI IDs database URI");

        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpGet req = new HttpGet(uri);

        try (CloseableHttpResponse response = httpclient.execute(req)) {
            InputStream is = response.getEntity().getContent();
            this._loadStream(is);
        }
    }

    /**
     * Load the pci.ids file from the provided input stream. Unsynchronized function for internal
     * use only.
     *
     * @param is Input stream holding the database file contents
     * @throws IOException if the database file cannot be read, is malformed or otherwise
     * unprocessable
     */
    private void _loadStream(InputStream is) throws IOException {
        ArgumentValidator.requireNonNull(is, "PCI IDs database InputStream");

        // Lock and clear existing database, if required
        if (this.ready) {
            this.ready = false;
            this.vendorDatabase.clear();
            this.deviceClassDatabase.clear();
        }

        this.parser.parseDatabaseFile(is, this.vendorDatabase, this.deviceClassDatabase);

        this.ready = true;
    }
}
