export interface SelectRequest {
  productName: string;
  productOption?: string;
  imageUrls?: string[];
  quantity: number;
}

export interface SelectResponse {
  success: boolean;
  data?: {
    supplier: 'TAOBAO';
    productName: string;
    productOption: string;
    productUrl: string;
    quantity: number;
    unitPrice: number;
  };
  error?: string;
}
