import { post } from './request'

/** 点赞(1)/点踩(-1)，可附评论 */
export function submitFeedback(qaId: number, rating: 1 | -1, comment?: string) {
  return post<void>('/feedback', { qaId, rating, comment })
}
