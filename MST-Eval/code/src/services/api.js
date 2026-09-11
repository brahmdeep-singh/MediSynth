// Central place that will eventually hold the real Axios/Fetch client.
// Every *Service.js file imports MOCK_DELAY / simulateRequest from here so that
// swapping mocks for the real Spring Boot API later means editing this file
// (and each service's implementation) without touching any component.

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";
export const USE_MOCKS = (import.meta.env.VITE_USE_MOCKS ?? "true") !== "false";

const MOCK_DELAY = 400;

// Simulates a network round-trip. Swap this out for a real fetch/axios call
// (see the commented example below) once the Spring Boot backend exists.
export function simulateRequest(data, { delay = MOCK_DELAY, failRate = 0 } = {}) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (failRate > 0 && Math.random() < failRate) {
        reject(new Error("Network error - please try again."));
        return;
      }
      resolve(structuredClone ? structuredClone(data) : JSON.parse(JSON.stringify(data)));
    }, delay);
  });
}

/*
Future real implementation, e.g. in patientService.js:

import axios from "axios";
import { API_BASE_URL } from "./api";

export async function getPatients() {
  const res = await axios.get(`${API_BASE_URL}/patients`);
  return res.data;
}
*/
