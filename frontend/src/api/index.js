import axios from 'axios';
import toast from 'react-hot-toast';

// 401 응답 시 리다이렉트할 로그인 경로
const LOGIN_PATH = '/login';

// axios 인스턴스 — baseURL은 vite proxy(/api → localhost:8080) 경유 상대경로
const api = axios.create({
  baseURL: '/api',
  withCredentials: true, // HttpOnly 쿠키(JWT) 전송
});

// 응답 인터셉터
// - 성공: CommonResponse 그대로 통과 (각 함수에서 res.data.data 사용)
// - 401: 세션 만료 → localStorage 정리 후 로그인 페이지로 강제 이동 (검증 Y-5)
// - 그 외 에러: 서버 메시지를 toast로 일괄 표출
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || '요청 처리 중 오류가 발생했습니다.';

    if (status === 401) {
      // 인증 만료 — 저장된 유저 정보만 제거 후 로그인 페이지로 (다른 키까지 지우지 않게 clear() 금지)
      localStorage.removeItem('userInfo');
      if (window.location.pathname !== LOGIN_PATH) {
        window.location.href = LOGIN_PATH;
      }
    } else {
      toast.error(message);
    }
    return Promise.reject(error);
  },
);

export default api;
