const API = "/api";

let currentUser =
    JSON.parse(localStorage.getItem("campusparkUser")) || null;

let vehicles = [];
let slots = [];
let history = [];
let selectedSlotId = null;


// ==========================================
// START
// ==========================================

document.addEventListener("DOMContentLoaded", () => {

    document.getElementById("loginForm")
        .addEventListener("submit", login);

    document.getElementById("registerForm")
        .addEventListener("submit", register);

    document.getElementById("vehicleForm")
        .addEventListener("submit", addVehicle);

    if (currentUser) {
        showApp();
    } else {
        showAuth("login");
    }
});


// ==========================================
// AUTH
// ==========================================

function showAuth(type) {

    const loginForm =
        document.getElementById("loginForm");

    const registerForm =
        document.getElementById("registerForm");

    const loginTab =
        document.getElementById("loginTab");

    const registerTab =
        document.getElementById("registerTab");

    if (type === "login") {

        loginForm.classList.remove("hidden");
        registerForm.classList.add("hidden");

        loginTab.classList.add("active");
        registerTab.classList.remove("active");

    } else {

        loginForm.classList.add("hidden");
        registerForm.classList.remove("hidden");

        loginTab.classList.remove("active");
        registerTab.classList.add("active");
    }
}


async function register(event) {

    event.preventDefault();

    const data = new URLSearchParams();

    data.append(
        "name",
        document.getElementById("regName").value.trim()
    );

    data.append(
        "studentId",
        document.getElementById("regStudentId").value.trim()
    );

    data.append(
        "email",
        document.getElementById("regEmail").value.trim()
    );

    data.append(
        "phone",
        document.getElementById("regPhone").value.trim()
    );

    data.append(
        "password",
        document.getElementById("regPassword").value
    );

    try {

        const response =
            await fetch(API + "/register", {
                method: "POST",
                body: data
            });

        const result =
            await response.json();

        if (!response.ok) {

            showToast(
                result.message ||
                "Registration failed."
            );

            return;
        }

        showToast(
            "Account created successfully. Please login."
        );

        document.getElementById("registerForm").reset();

        showAuth("login");

        document.getElementById("loginIdentity").value =
            result.user?.email || "";

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to connect to CampusPark backend."
        );
    }
}


async function login(event) {

    event.preventDefault();

    const data = new URLSearchParams();

    data.append(
        "identity",
        document.getElementById("loginIdentity").value.trim()
    );

    data.append(
        "password",
        document.getElementById("loginPassword").value
    );

    try {

        const response =
            await fetch(API + "/login", {
                method: "POST",
                body: data
            });

        const result =
            await response.json();

        if (!response.ok) {

            showToast(
                result.message ||
                "Login failed."
            );

            return;
        }

        currentUser = result.user;

        localStorage.setItem(
            "campusparkUser",
            JSON.stringify(currentUser)
        );

        showApp();

        showToast(
            "Welcome back, " + currentUser.name + "!"
        );

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to connect to CampusPark backend."
        );
    }
}


function logout() {

    localStorage.removeItem("campusparkUser");

    currentUser = null;

    document.getElementById("app")
        .classList.add("hidden");

    document.getElementById("authScreen")
        .classList.remove("hidden");

    document.getElementById("loginForm").reset();

    showAuth("login");

    showToast("You have been logged out.");
}


// ==========================================
// APP
// ==========================================

function showApp() {

    if (!currentUser) return;

    document.getElementById("authScreen")
        .classList.add("hidden");

    document.getElementById("app")
        .classList.remove("hidden");

    document.getElementById("userName").textContent =
        currentUser.name;

    document.getElementById("welcomeName").textContent =
        firstName(currentUser.name);

    document.getElementById("userRole").textContent =
        currentUser.role === "ADMIN"
            ? "Administrator"
            : "Student";

    document.getElementById("avatar").textContent =
        initials(currentUser.name);

    if (currentUser.role === "ADMIN") {

        document.getElementById("adminNav")
            .classList.remove("hidden");
    }

    openPage("dashboard");
}


function openPage(page) {

    document.querySelectorAll(".page")
        .forEach(p => p.classList.add("hidden"));

    const selected =
        document.getElementById("page-" + page);

    if (!selected) return;

    selected.classList.remove("hidden");

    document.querySelectorAll(".nav-item")
        .forEach(item => item.classList.remove("active"));

    const nav =
        document.querySelector(
            `.nav-item[data-page="${page}"]`
        );

    if (nav) {
        nav.classList.add("active");
    }

    const titles = {
        dashboard: ["OVERVIEW", "Dashboard"],
        vehicles: ["VEHICLE MANAGEMENT", "My Vehicles"],
        parking: ["PARKING", "Park Vehicle"],
        search: ["VEHICLE LOCATOR", "Find Vehicle"],
        history: ["ACTIVITY", "Parking History"],
        pass: ["ACTIVE PASS", "Parking Pass"],
        admin: ["ADMINISTRATION", "Admin Panel"]
    };

    document.getElementById("pageEyebrow").textContent =
        titles[page][0];

    document.getElementById("pageTitle").textContent =
        titles[page][1];

    if (page === "dashboard") {
        loadDashboard();
    }

    if (page === "vehicles") {
        loadVehicles();
    }

    if (page === "parking") {
        loadVehicles();
        loadSlots();
        updateCurrentParking();
    }

    if (page === "history") {
        loadHistory();
    }

    if (page === "pass") {
        loadPass();
    }

    if (page === "admin") {
        loadAdmin();
    }
}


// ==========================================
// DASHBOARD
// ==========================================

async function loadDashboard() {

    if (!currentUser) return;

    try {

        const response =
            await fetch(
                API +
                "/dashboard?userId=" +
                encodeURIComponent(currentUser.id)
            );

        const data =
            await response.json();

        if (!response.ok) {
            showToast(data.message || "Dashboard error.");
            return;
        }

        document.getElementById("availableCount")
            .textContent = data.availableSlots;

        document.getElementById("occupiedCount")
            .textContent = data.occupiedSlots;

        document.getElementById("vehicleCount")
            .textContent = data.myVehicles;

        document.getElementById("historyCount")
            .textContent = data.myHistory;

        const current =
            document.getElementById("currentParking");

        const badge =
            document.getElementById("currentBadge");

        if (data.currentParking) {

            badge.textContent = "PARKED";
            badge.className = "badge green";

            current.className =
                "current-parking";

            current.innerHTML = `
                <div class="current-info">

                    <div>
                        <span>Vehicle</span>
                        <strong>
                            ${escapeHtml(
                data.currentParking.vehicleNumber
            )}
                        </strong>
                    </div>

                    <div>
                        <span>Slot</span>
                        <strong>
                            ${escapeHtml(
                data.currentParking.slot
            )}
                        </strong>
                    </div>

                    <div>
                        <span>Parked At</span>
                        <strong>
                            ${escapeHtml(
                data.currentParking.parkedAt
            )}
                        </strong>
                    </div>

                </div>

                <button
                    class="danger-btn"
                    style="margin-top:18px"
                    onclick="removeVehicle()">
                    Remove Vehicle
                </button>
            `;

        } else {

            badge.textContent = "NOT PARKED";
            badge.className = "badge neutral";

            current.className =
                "current-parking empty-state";

            current.innerHTML = `
                <div class="empty-icon">🅿️</div>
                <h3>No active parking</h3>
                <p>
                    Park one of your registered vehicles
                    to see it here.
                </p>

                <button class="outline-btn"
                        onclick="openPage('parking')">
                    Park Vehicle
                </button>
            `;
        }

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to load dashboard."
        );
    }
}


// ==========================================
// VEHICLES
// ==========================================

async function loadVehicles() {

    if (!currentUser) return;

    try {

        const response =
            await fetch(
                API +
                "/vehicles?userId=" +
                encodeURIComponent(currentUser.id)
            );

        vehicles = await response.json();

        if (!response.ok) {
            showToast(
                vehicles.message ||
                "Unable to load vehicles."
            );
            return;
        }

        renderVehicles();
        fillVehicleSelect();

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to load vehicles."
        );
    }
}


function renderVehicles() {

    const container =
        document.getElementById("vehicleList");

    if (!vehicles.length) {

        container.innerHTML = `
            <div class="panel empty-state"
                 style="grid-column:1/-1">

                <div class="empty-icon">🚗</div>

                <h3>No vehicles registered</h3>

                <p>
                    Add your first vehicle below.
                </p>

            </div>
        `;

        return;
    }

    container.innerHTML =
        vehicles.map(vehicle => {

            const icon =
                vehicle.vehicleType === "CAR"
                    ? "🚗"
                    : vehicle.vehicleType === "BIKE"
                        ? "🏍️"
                        : vehicle.vehicleType === "SCOOTER"
                            ? "🛵"
                            : "🚙";

            return `
                <div class="vehicle-card">

                    <div class="vehicle-icon">
                        ${icon}
                    </div>

                    <h3>
                        ${escapeHtml(
                vehicle.vehicleNumber
            )}
                    </h3>

                    <p>
                        ${escapeHtml(
                vehicle.vehicleModel ||
                "Model not specified"
            )}
                    </p>

                    <div class="vehicle-details">

                        <div>
                            <span>TYPE</span>
                            <strong>
                                ${escapeHtml(
                vehicle.vehicleType
            )}
                            </strong>
                        </div>

                        <div>
                            <span>COLOR</span>
                            <strong>
                                ${escapeHtml(
                vehicle.vehicleColor ||
                "-"
            )}
                            </strong>
                        </div>

                        <div>
                            <span>FUEL</span>
                            <strong>
                                ${escapeHtml(
                vehicle.fuelType ||
                "-"
            )}
                            </strong>
                        </div>

                    </div>

                    <div class="card-actions">

                        <button
                            onclick="parkThisVehicle(${vehicle.id})">
                            Park Now
                        </button>

                        <button
                            onclick="deleteVehicle(${vehicle.id})">
                            Delete
                        </button>

                    </div>

                </div>
            `;
        }).join("");
}


async function addVehicle(event) {

    event.preventDefault();

    const data = new URLSearchParams();

    data.append(
        "userId",
        currentUser.id
    );

    data.append(
        "vehicleNumber",
        document.getElementById("vehicleNumber")
            .value.trim()
    );

    data.append(
        "vehicleType",
        document.getElementById("vehicleType")
            .value
    );

    data.append(
        "vehicleModel",
        document.getElementById("vehicleModel")
            .value.trim()
    );

    data.append(
        "vehicleColor",
        document.getElementById("vehicleColor")
            .value.trim()
    );

    data.append(
        "fuelType",
        document.getElementById("fuelType")
            .value
    );

    try {

        const response =
            await fetch(API + "/vehicles", {
                method: "POST",
                body: data
            });

        const result =
            await response.json();

        if (!response.ok) {

            showToast(
                result.message ||
                "Unable to add vehicle."
            );

            return;
        }

        document.getElementById("vehicleForm")
            .reset();

        showToast(
            "Vehicle added successfully."
        );

        await loadVehicles();

        loadDashboard();

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to add vehicle."
        );
    }
}


async function deleteVehicle(id) {

    if (!confirm(
        "Delete this vehicle from your account?"
    )) {
        return;
    }

    try {

        const response =
            await fetch(
                API +
                "/vehicle?userId=" +
                encodeURIComponent(currentUser.id) +
                "&vehicleId=" +
                encodeURIComponent(id),
                {
                    method: "DELETE"
                }
            );

        const result =
            await response.json();

        showToast(
            result.message ||
            "Vehicle operation completed."
        );

        if (response.ok) {
            loadVehicles();
            loadDashboard();
        }

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to delete vehicle."
        );
    }
}


function parkThisVehicle(id) {

    openPage("parking");

    setTimeout(() => {

        const select =
            document.getElementById(
                "parkVehicleSelect"
            );

        select.value = String(id);

        renderSlots();

    }, 150);
}


// ==========================================
// PARKING
// ==========================================

async function loadSlots() {

    try {

        const response =
            await fetch(API + "/slots");

        slots = await response.json();

        if (!response.ok) {
            showToast(
                slots.message ||
                "Unable to load slots."
            );
            return;
        }

        renderSlots();

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to load parking slots."
        );
    }
}


function fillVehicleSelect() {

    const select =
        document.getElementById(
            "parkVehicleSelect"
        );

    if (!select) return;

    const previous = select.value;

    select.innerHTML = `
        <option value="">
            Select a registered vehicle
        </option>
    `;

    vehicles.forEach(vehicle => {

        select.innerHTML += `
            <option value="${vehicle.id}">
                ${escapeHtml(vehicle.vehicleNumber)}
                — ${escapeHtml(vehicle.vehicleType)}
            </option>
        `;
    });

    if (previous) {
        select.value = previous;
    }

    select.onchange = () => {
        selectedSlotId = null;
        document.getElementById(
            "selectedSlotBox"
        ).classList.add("hidden");
        renderSlots();
    };
}


function renderSlots() {

    const container =
        document.getElementById("slotGrid");

    if (!container) return;

    const vehicleId =
        document.getElementById(
            "parkVehicleSelect"
        )?.value;

    const vehicle =
        vehicles.find(
            v => String(v.id) === String(vehicleId)
        );

    container.innerHTML =
        slots.map(slot => {

            const compatible =
                !vehicle ||
                slot.allowedType === "ALL" ||
                slot.allowedType === vehicle.vehicleType;

            const available =
                slot.available && compatible;

            const selected =
                String(selectedSlotId) ===
                String(slot.id);

            if (!available) {

                return `
                    <div class="slot occupied">
                        <strong>${escapeHtml(slot.slot)}</strong>
                        <small>
                            ${slot.available
                        ? "Not compatible"
                        : "Occupied"
                    }
                        </small>
                    </div>
                `;
            }

            return `
                <button
                    class="slot available ${selected ? "selected" : ""}"
                    onclick="selectSlot(${slot.id})">

                    <strong>
                        ${escapeHtml(slot.slot)}
                    </strong>

                    <small>
                        Available
                    </small>

                </button>
            `;
        }).join("");
}


function selectSlot(id) {

    const vehicleId =
        document.getElementById(
            "parkVehicleSelect"
        ).value;

    if (!vehicleId) {

        showToast(
            "Select a vehicle first."
        );

        return;
    }

    selectedSlotId = id;

    const slot =
        slots.find(
            s => String(s.id) === String(id)
        );

    document.getElementById(
        "selectedSlot"
    ).textContent = slot.slot;

    document.getElementById(
        "selectedSlotBox"
    ).classList.remove("hidden");

    renderSlots();
}


async function confirmParking() {

    const vehicleId =
        document.getElementById(
            "parkVehicleSelect"
        ).value;

    if (!vehicleId || !selectedSlotId) {

        showToast(
            "Select a vehicle and parking slot."
        );

        return;
    }

    const data = new URLSearchParams();

    data.append(
        "userId",
        currentUser.id
    );

    data.append(
        "vehicleId",
        vehicleId
    );

    data.append(
        "slotId",
        selectedSlotId
    );

    try {

        const response =
            await fetch(API + "/park", {
                method: "POST",
                body: data
            });

        const result =
            await response.json();

        if (!response.ok) {

            showToast(
                result.message ||
                "Unable to park vehicle."
            );

            return;
        }

        showToast(
            "Vehicle parked in " +
            result.slot +
            "."
        );

        selectedSlotId = null;

        document.getElementById(
            "selectedSlotBox"
        ).classList.add("hidden");

        await loadSlots();

        updateCurrentParking();

        loadDashboard();

        loadPass();

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to park vehicle."
        );
    }
}


async function updateCurrentParking() {

    try {

        const response =
            await fetch(
                API +
                "/dashboard?userId=" +
                currentUser.id
            );

        const data =
            await response.json();

        const box =
            document.getElementById("removeBox");

        if (!box) return;

        if (data.currentParking) {

            box.classList.remove("hidden");

            document.getElementById(
                "removeDetails"
            ).textContent =
                data.currentParking.vehicleNumber +
                " is parked in slot " +
                data.currentParking.slot +
                " since " +
                data.currentParking.parkedAt;

        } else {

            box.classList.add("hidden");
        }

    } catch (error) {

        console.error(error);
    }
}


async function removeVehicle() {

    if (!confirm(
        "Remove your currently parked vehicle?"
    )) {
        return;
    }

    const data = new URLSearchParams();

    data.append(
        "userId",
        currentUser.id
    );

    try {

        const response =
            await fetch(API + "/remove", {
                method: "POST",
                body: data
            });

        const result =
            await response.json();

        if (!response.ok) {

            showToast(
                result.message ||
                "Unable to remove vehicle."
            );

            return;
        }

        showToast(
            result.vehicleNumber +
            " removed from " +
            result.slot +
            "."
        );

        await loadSlots();

        updateCurrentParking();

        loadDashboard();

        loadHistory();

        loadPass();

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to remove vehicle."
        );
    }
}


// ==========================================
// SEARCH
// ==========================================

async function searchVehicle() {

    const number =
        document.getElementById(
            "searchVehicleNumber"
        ).value.trim();

    if (!number) {

        showToast(
            "Enter a vehicle number."
        );

        return;
    }

    const resultBox =
        document.getElementById(
            "searchResult"
        );

    try {

        const response =
            await fetch(
                API +
                "/search?vehicleNumber=" +
                encodeURIComponent(number)
            );

        const data =
            await response.json();

        resultBox.classList.remove(
            "hidden",
            "success",
            "error"
        );

        if (!response.ok) {

            resultBox.classList.add("error");

            resultBox.innerHTML = `
                <h3>Vehicle not found</h3>
                <p>
                    ${escapeHtml(
                data.message ||
                "No vehicle found."
            )}
                </p>
            `;

            return;
        }

        resultBox.classList.add("success");

        resultBox.innerHTML = `
            <div class="search-main">

                <div class="vehicle-icon">
                    ${data.vehicleType === "CAR"
                ? "🚗"
                : data.vehicleType === "BIKE"
                    ? "🏍️"
                    : "🛵"
            }
                </div>

                <div>

                    <span class="eyebrow">
                        VEHICLE FOUND
                    </span>

                    <h2>
                        ${escapeHtml(
                data.vehicleNumber
            )}
                    </h2>

                    <p>
                        Owner:
                        ${escapeHtml(data.owner)}
                    </p>

                </div>

            </div>

            <div class="pass-grid"
                 style="color:#172033">

                <div>
                    <span style="color:#69758a">
                        TYPE
                    </span>
                    <strong>
                        ${escapeHtml(
                data.vehicleType
            )}
                    </strong>
                </div>

                <div>
                    <span style="color:#69758a">
                        STATUS
                    </span>
                    <strong>
                        ${escapeHtml(data.status)}
                    </strong>
                </div>

                <div>
                    <span style="color:#69758a">
                        SLOT
                    </span>
                    <strong>
                        ${escapeHtml(data.slot || "-")}
                    </strong>
                </div>

            </div>
        `;

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to search vehicle."
        );
    }
}


// ==========================================
// HISTORY
// ==========================================

async function loadHistory() {

    if (!currentUser) return;

    try {

        const response =
            await fetch(
                API +
                "/history?userId=" +
                currentUser.id
            );

        history =
            await response.json();

        if (!response.ok) {

            showToast(
                history.message ||
                "Unable to load history."
            );

            return;
        }

        renderHistory();

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to load parking history."
        );
    }
}


function renderHistory() {

    const table =
        document.getElementById(
            "historyTable"
        );

    if (!table) return;

    const filter =
        document.getElementById(
            "historySearch"
        )?.value
            .trim()
            .toLowerCase() || "";

    const rows =
        history.filter(item =>
            item.vehicleNumber
                .toLowerCase()
                .includes(filter)
        );

    if (!rows.length) {

        table.innerHTML = `
            <tr>
                <td colspan="6"
                    style="text-align:center;color:#69758a">
                    No parking history found.
                </td>
            </tr>
        `;

        return;
    }

    table.innerHTML =
        rows.map(item => {

            const active =
                item.status === "ACTIVE";

            return `
                <tr>

                    <td>
                        <strong>
                            ${escapeHtml(
                item.vehicleNumber
            )}
                        </strong>
                    </td>

                    <td>
                        ${escapeHtml(
                item.vehicleType
            )}
                    </td>

                    <td>
                        ${escapeHtml(
                item.slot
            )}
                    </td>

                    <td>
                        ${escapeHtml(
                item.parkedAt
            )}
                    </td>

                    <td>
                        ${item.removedAt
                    ? escapeHtml(
                        item.removedAt
                    )
                    : "-"
                }
                    </td>

                    <td>
                        <span class="status ${active
                    ? "active"
                    : "completed"
                }">
                            ${active
                    ? "ACTIVE"
                    : "COMPLETED"
                }
                        </span>
                    </td>

                </tr>
            `;
        }).join("");
}


// ==========================================
// PASS
// ==========================================

async function loadPass() {

    try {

        const response =
            await fetch(
                API +
                "/dashboard?userId=" +
                currentUser.id
            );

        const data =
            await response.json();

        const pass =
            document.getElementById("passCard");

        if (!data.currentParking) {

            pass.className =
                "pass-card pass-empty";

            pass.innerHTML = `
                <h2>No active parking pass</h2>
                <p>
                    Park a vehicle to generate your
                    current parking pass.
                </p>

                <button class="outline-btn"
                        onclick="openPage('parking')">
                    Park Vehicle
                </button>
            `;

            return;
        }

        pass.className = "pass-card";

        pass.innerHTML = `
            <div class="pass-top">

                <div>
                    <div class="pass-label">
                        CAMPUSPARK
                    </div>

                    <h2>Parking Pass</h2>
                </div>

                <span class="status-pill">
                    <i></i> ACTIVE
                </span>

            </div>

            <div class="pass-grid">

                <div>
                    <span>STUDENT</span>
                    <strong>
                        ${escapeHtml(
            currentUser.name
        )}
                    </strong>
                </div>

                <div>
                    <span>STUDENT ID</span>
                    <strong>
                        ${escapeHtml(
            currentUser.studentId
        )}
                    </strong>
                </div>

                <div>
                    <span>VEHICLE</span>
                    <strong>
                        ${escapeHtml(
            data.currentParking.vehicleNumber
        )}
                    </strong>
                </div>

                <div>
                    <span>VEHICLE TYPE</span>
                    <strong>
                        ${escapeHtml(
            data.currentParking.vehicleType
        )}
                    </strong>
                </div>

                <div>
                    <span>PARKING SLOT</span>
                    <strong>
                        ${escapeHtml(
            data.currentParking.slot
        )}
                    </strong>
                </div>

                <div>
                    <span>PARKED AT</span>
                    <strong>
                        ${escapeHtml(
            data.currentParking.parkedAt
        )}
                    </strong>
                </div>

            </div>
        `;

    } catch (error) {

        console.error(error);
    }
}


// ==========================================
// ADMIN
// ==========================================

async function loadAdmin() {

    if (currentUser.role !== "ADMIN") {
        return;
    }

    try {

        const response =
            await fetch(
                API +
                "/admin?userId=" +
                currentUser.id
            );

        const data =
            await response.json();

        if (!response.ok) {

            showToast(
                data.message ||
                "Admin access denied."
            );

            return;
        }

        document.getElementById("adminUsers")
            .textContent = data.users;

        document.getElementById("adminVehicles")
            .textContent = data.vehicles;

        document.getElementById("adminAvailable")
            .textContent = data.available;

        document.getElementById("adminActive")
            .textContent = data.activeParking;

    } catch (error) {

        console.error(error);

        showToast(
            "Unable to load admin panel."
        );
    }
}


// ==========================================
// UI HELPERS
// ==========================================

function showToast(message) {

    const toast =
        document.getElementById("toast");

    toast.textContent = message;

    toast.classList.add("show");

    clearTimeout(window.toastTimer);

    window.toastTimer =
        setTimeout(() => {
            toast.classList.remove("show");
        }, 3000);
}


function firstName(name) {

    return (name || "User")
        .trim()
        .split(/\s+/)[0];
}


function initials(name) {

    return (name || "U")
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map(part => part[0])
        .join("")
        .toUpperCase();
}


function escapeHtml(value) {

    if (value === null ||
        value === undefined) {

        return "";
    }

    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
