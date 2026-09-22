/**
 * Room and Housekeeping Service Layer for Spring Boot REST backend (JavaScript)
 */
import { apiClient } from './apiClient';

export const HOTEL_BRANCHES = [
  { id: 'all', name: 'Toàn Chuỗi Sao Mai (Tất cả)', shortName: 'Toàn Chuỗi', city: 'Việt Nam', roomCount: 248 },
  { id: 'sm-dn', name: 'Sao Mai Grand Hotel - Đà Nẵng', shortName: 'Đà Nẵng', city: 'Đà Nẵng', roomCount: 96 },
  { id: 'sm-ha', name: 'Sao Mai Boutique - Hội An', shortName: 'Hội An', city: 'Quảng Nam', roomCount: 64 },
  { id: 'sm-nt', name: 'Sao Mai Resort & Spa - Nha Trang', shortName: 'Nha Trang', city: 'Khánh Hòa', roomCount: 88 },
];

export const INITIAL_ROOMS = [
  // Floor 1
  { id: 'r-101', roomNumber: '101', roomType: 'Standard', floor: 1, status: 'READY', lastCleaned: '08:45' },
  { id: 'r-102', roomNumber: '102', roomType: 'Standard', floor: 1, status: 'OCCUPIED', guestName: 'Nguyễn Văn An', lastCleaned: 'Hôm qua' },
  { id: 'r-103', roomNumber: '103', roomType: 'Deluxe', floor: 1, status: 'DIRTY', housekeeper: 'Chưa phân công', lastCleaned: '10:00' },
  { id: 'r-104', roomNumber: '104', roomType: 'Deluxe', floor: 1, status: 'CLEANING', housekeeper: 'Nguyễn Thị Mai', lastCleaned: 'Đang thực hiện' },
  { id: 'r-105', roomNumber: '105', roomType: 'Suite', floor: 1, status: 'INSPECTING', housekeeper: 'Trần Văn Bình', lastCleaned: '09:15' },
  { id: 'r-106', roomNumber: '106', roomType: 'Standard', floor: 1, status: 'READY', lastCleaned: '08:30' },
  { id: 'r-107', roomNumber: '107', roomType: 'Deluxe', floor: 1, status: 'UNAVAILABLE', guestName: 'Bảo trì điều hòa' },
  { id: 'r-108', roomNumber: '108', roomType: 'Suite', floor: 1, status: 'OCCUPIED', guestName: 'Trần Thảo My' },

  // Floor 2
  { id: 'r-201', roomNumber: '201', roomType: 'Deluxe', floor: 2, status: 'OCCUPIED', guestName: 'Đỗ Quốc Khánh' },
  { id: 'r-202', roomNumber: '202', roomType: 'Deluxe', floor: 2, status: 'READY', lastCleaned: '07:50' },
  { id: 'r-203', roomNumber: '203', roomType: 'Suite', floor: 2, status: 'DIRTY', housekeeper: 'Chưa phân công' },
  { id: 'r-204', roomNumber: '204', roomType: 'Suite', floor: 2, status: 'OCCUPIED', guestName: 'Phạm Hồng Nhung' },
  { id: 'r-205', roomNumber: '205', roomType: 'Executive', floor: 2, status: 'READY', lastCleaned: '08:15' },
  { id: 'r-206', roomNumber: '206', roomType: 'Deluxe', floor: 2, status: 'CLEANING', housekeeper: 'Lê Thị Thu' },
  { id: 'r-207', roomNumber: '207', roomType: 'Standard', floor: 2, status: 'INSPECTING', housekeeper: 'Vũ Đức Thành' },
  { id: 'r-208', roomNumber: '208', roomType: 'Standard', floor: 2, status: 'READY', lastCleaned: '09:00' },

  // Floor 3
  { id: 'r-301', roomNumber: '301', roomType: 'Executive', floor: 3, status: 'OCCUPIED', guestName: 'Mr. Johnathan Lee' },
  { id: 'r-302', roomNumber: '302', roomType: 'Executive', floor: 3, status: 'READY', lastCleaned: '08:30' },
  { id: 'r-303', roomNumber: '303', roomType: 'Suite', floor: 3, status: 'DIRTY' },
  { id: 'r-304', roomNumber: '304', roomType: 'Suite', floor: 3, status: 'OCCUPIED', guestName: 'Bùi Gia Huy' },
  { id: 'r-305', roomNumber: '305', roomType: 'Deluxe', floor: 3, status: 'READY', lastCleaned: '08:00' },
  { id: 'r-306', roomNumber: '306', roomType: 'Standard', floor: 3, status: 'UNAVAILABLE', guestName: 'Sơn lại trần' },
];

let clientRoomStore = [...INITIAL_ROOMS];

export class RoomService {
  /**
   * Fetch rooms: GET /api/v1/rooms?hotelId={hotelId}
   */
  static async getRooms(hotelId) {
    try {
      return await apiClient.get(`/rooms${hotelId ? `?hotelId=${hotelId}` : ''}`);
    } catch (err) {
      return [...clientRoomStore];
    }
  }

  /**
   * Update room status: PATCH /api/v1/rooms/{roomId}/status
   */
  static async updateRoomStatus(roomId, status) {
    try {
      return await apiClient.patch(`/rooms/${roomId}/status`, { status });
    } catch (err) {
      const idx = clientRoomStore.findIndex((r) => r.id === roomId);
      if (idx !== -1) {
        clientRoomStore[idx] = { ...clientRoomStore[idx], status };
        return clientRoomStore[idx];
      }
      throw new Error(`Room ${roomId} not found`);
    }
  }
}
